package br.com.unifinancas.application.notificacao;

import br.com.unifinancas.domain.notificacao.CanalNotificacao;
import br.com.unifinancas.domain.notificacao.Notificacao;
import br.com.unifinancas.domain.notificacao.NotificacaoRepository;
import br.com.unifinancas.domain.notificacao.TipoNotificacao;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-14, SAD Sec4.16, Sec6.4 (diagrama de sequencia): "INSERT notificacao
 * (IN_APP sempre; PUSH/EMAIL conforme preferencia)" - nao ha ainda um
 * mecanismo de preferencia de canal por usuario no roadmap, entao este
 * servico so garante o IN_APP incondicionalmente - canais adicionais sao
 * uma decisao explicita de quem chama, nao algo que este metodo infere
 * sozinho. {@code IN_APP} e "entregue" so por existir na tabela; uma linha
 * PUSH/EMAIL, ao ser persistida, enfileira o envio assincrono via {@link
 * EnfileiradorDeEnvioPort} (T1.5.6) - nunca envia sincronamente aqui.
 *
 * <p>{@link #criar} nao recebe {@link TenantContext}: quem chama e outro
 * Application service reagindo a um evento (T1.5.3) ou um job assincrono
 * (T1.5.4), nunca diretamente um controller - a autorizacao por perfil
 * (Sec8.2) nao se aplica a um efeito colateral disparado pelo sistema.
 * {@link #listar} e {@link #marcarComoLida} (T1.5.5), ao contrario, sao
 * chamados por controller e recebem {@code TenantContext} normalmente -
 * leitura/marcar-como-lida e permitido a qualquer perfil (e pessoal ao
 * usuario, nao uma acao de gestao do Ambiente Financeiro).
 */
@Service
public class NotificacaoService {

    private static final int LIMITE_PADRAO = 20;
    private static final int LIMITE_MAXIMO = 100;

    private final NotificacaoRepository notificacaoRepository;
    private final EnfileiradorDeEnvioPort enfileiradorDeEnvioPort;

    public NotificacaoService(NotificacaoRepository notificacaoRepository, EnfileiradorDeEnvioPort enfileiradorDeEnvioPort) {
        this.notificacaoRepository = notificacaoRepository;
        this.enfileiradorDeEnvioPort = enfileiradorDeEnvioPort;
    }

    /**
     * Cria uma Notificacao IN_APP (sempre) e, se solicitado, uma linha
     * adicional por canal em {@code canaisAdicionais} - cada canal e uma
     * linha propria (Sec4.16: {@code canal} e um valor unico por registro,
     * nao um conjunto).
     */
    @Transactional
    public List<Notificacao> criar(UUID ambienteFinanceiroId, UUID usuarioId, TipoNotificacao tipo, String titulo,
            String mensagem, String referenciaTipo, UUID referenciaId, Set<CanalNotificacao> canaisAdicionais) {
        List<Notificacao> criadas = new ArrayList<>();
        criadas.add(persistir(ambienteFinanceiroId, usuarioId, tipo, titulo, mensagem, referenciaTipo, referenciaId,
                CanalNotificacao.IN_APP));

        if (canaisAdicionais != null) {
            for (CanalNotificacao canal : canaisAdicionais) {
                if (canal != CanalNotificacao.IN_APP) {
                    criadas.add(persistir(ambienteFinanceiroId, usuarioId, tipo, titulo, mensagem, referenciaTipo,
                            referenciaId, canal));
                }
            }
        }
        return criadas;
    }

    private Notificacao persistir(UUID ambienteFinanceiroId, UUID usuarioId, TipoNotificacao tipo, String titulo,
            String mensagem, String referenciaTipo, UUID referenciaId, CanalNotificacao canal) {
        Notificacao notificacao =
                Notificacao.novo(ambienteFinanceiroId, usuarioId, tipo, titulo, mensagem, referenciaTipo, referenciaId, canal);
        notificacaoRepository.save(notificacao);
        if (canal != CanalNotificacao.IN_APP) {
            enfileiradorDeEnvioPort.enfileirar(notificacao.getId());
        }
        return notificacao;
    }

    /**
     * T1.5.5, SAD Sec7.2: lista a "central de notificacoes" do usuario no
     * ambiente ativo, paginada por cursor (mais recente primeiro). Restrita
     * ao canal IN_APP (decisao consultada com o usuario): um mesmo evento
     * multicanal (Sec4.16) vira varias linhas na tabela (uma por canal) -
     * sem esse filtro, o mesmo aviso apareceria repetido na lista uma vez
     * por canal acionado. Linhas PUSH/EMAIL continuam existindo so como
     * rastro de envio por canal (T1.5.6).
     */
    public PaginaNotificacoes listar(TenantContext contexto, Boolean lida, String cursorBruto, Integer limite) {
        int tamanhoPagina = validarLimite(limite);
        CursorNotificacao cursor = cursorBruto != null ? CursorNotificacao.decodificar(cursorBruto) : null;

        Specification<Notificacao> especificacao = construirEspecificacao(contexto, lida, cursor);
        Sort ordenacao = Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id"));

        List<Notificacao> encontradas =
                notificacaoRepository.findAll(especificacao, PageRequest.of(0, tamanhoPagina + 1, ordenacao)).getContent();

        boolean temProximaPagina = encontradas.size() > tamanhoPagina;
        List<Notificacao> pagina = temProximaPagina ? encontradas.subList(0, tamanhoPagina) : encontradas;

        String proximoCursor = null;
        if (temProximaPagina) {
            Notificacao ultima = pagina.get(pagina.size() - 1);
            proximoCursor = new CursorNotificacao(ultima.getCriadoEm(), ultima.getId()).codificar();
        }

        return new PaginaNotificacoes(pagina.stream().map(this::paraResumo).toList(), proximoCursor);
    }

    /** T1.5.5: escopado por usuarioId (nao so ambienteFinanceiroId) - Notificacao e pessoal, nao compartilhada entre membros. */
    @Transactional
    public void marcarComoLida(TenantContext contexto, UUID notificacaoId) {
        Notificacao notificacao = notificacaoRepository
                .findByIdAndUsuarioIdAndAmbienteFinanceiroId(notificacaoId, contexto.usuarioId(), contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "NOTIFICACAO_NAO_ENCONTRADA", "Notificacao nao encontrada para este usuario."));
        notificacao.marcarComoLida();
        notificacaoRepository.save(notificacao);
    }

    private int validarLimite(Integer limite) {
        int valor = limite != null ? limite : LIMITE_PADRAO;
        if (valor < 1 || valor > LIMITE_MAXIMO) {
            throw new RegraDeNegocioException("LIMITE_INVALIDO", "limit deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return valor;
    }

    private Specification<Notificacao> construirEspecificacao(TenantContext contexto, Boolean lida, CursorNotificacao cursor) {
        Specification<Notificacao> especificacao = (root, query, cb) -> cb.and(
                cb.equal(root.get("ambienteFinanceiroId"), contexto.ambienteFinanceiroId()),
                cb.equal(root.get("usuarioId"), contexto.usuarioId()),
                cb.equal(root.get("canal"), CanalNotificacao.IN_APP));

        if (lida != null) {
            especificacao = especificacao.and((root, query, cb) -> cb.equal(root.get("lida"), lida));
        }
        if (cursor != null) {
            especificacao = especificacao.and((root, query, cb) -> cb.or(cb.lessThan(root.get("criadoEm"), cursor.criadoEm()),
                    cb.and(cb.equal(root.get("criadoEm"), cursor.criadoEm()), cb.lessThan(root.get("id"), cursor.id()))));
        }
        return especificacao;
    }

    private NotificacaoResumo paraResumo(Notificacao notificacao) {
        return new NotificacaoResumo(notificacao.getId(), notificacao.getTipo(), notificacao.getTitulo(),
                notificacao.getMensagem(), notificacao.getReferenciaTipo(), notificacao.getReferenciaId(),
                notificacao.getCanal(), notificacao.isLida(), notificacao.getEnviadaEm(), notificacao.getCriadoEm());
    }
}
