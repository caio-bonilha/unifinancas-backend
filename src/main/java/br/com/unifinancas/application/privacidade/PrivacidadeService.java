package br.com.unifinancas.application.privacidade;

import br.com.unifinancas.application.ambientefinanceiro.AmbienteFinanceiroService;
import br.com.unifinancas.application.categoria.CategoriaResumo;
import br.com.unifinancas.application.contabancaria.ContaBancariaResumo;
import br.com.unifinancas.application.contato.ContatoResumo;
import br.com.unifinancas.application.lancamento.LancamentoResumo;
import br.com.unifinancas.application.lancamentorecorrente.LancamentoRecorrenteResumo;
import br.com.unifinancas.application.movimentacao.MovimentacaoResumo;
import br.com.unifinancas.application.orcamentometa.OrcamentoMetaResumo;
import br.com.unifinancas.application.privacidade.ExportacaoDadosResumo.AmbienteExportado;
import br.com.unifinancas.application.privacidade.ExportacaoDadosResumo.UsuarioExportado;
import br.com.unifinancas.application.usuario.EmissorTokenPort;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiroRepository;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.StatusVinculo;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import br.com.unifinancas.domain.categoria.Categoria;
import br.com.unifinancas.domain.categoria.CategoriaRepository;
import br.com.unifinancas.domain.contabancaria.ContaBancaria;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.contato.Contato;
import br.com.unifinancas.domain.contato.ContatoRepository;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrente;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrenteRepository;
import br.com.unifinancas.domain.movimentacao.Movimentacao;
import br.com.unifinancas.domain.movimentacao.MovimentacaoRepository;
import br.com.unifinancas.domain.orcamentometa.OrcamentoMeta;
import br.com.unifinancas.domain.orcamentometa.OrcamentoMetaRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.usuario.StatusUsuario;
import br.com.unifinancas.domain.usuario.Usuario;
import br.com.unifinancas.domain.usuario.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T1.11.4, RNF-09 (LGPD - portabilidade e direito ao esquecimento, RN-13).
 * Nao recebe {@link br.com.unifinancas.domain.shared.TenantContext}: as duas
 * operacoes atravessam todos os Ambientes Financeiros do usuario por
 * definicao (o proprio pedido nao esta escopado a um Ambiente ja
 * selecionado) - mesmo raciocinio de {@code AmbienteFinanceiroService},
 * so com {@code usuarioId} vindo direto do token (Sec7.4).
 */
@Service
public class PrivacidadeService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository;
    private final AmbienteFinanceiroRepository ambienteFinanceiroRepository;
    private final AmbienteFinanceiroService ambienteFinanceiroService;
    private final ContaBancariaRepository contaBancariaRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContatoRepository contatoRepository;
    private final LancamentoRepository lancamentoRepository;
    private final LancamentoRecorrenteRepository lancamentoRecorrenteRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final OrcamentoMetaRepository orcamentoMetaRepository;
    private final EmissorTokenPort emissorTokenPort;

    public PrivacidadeService(UsuarioRepository usuarioRepository,
            UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository,
            AmbienteFinanceiroRepository ambienteFinanceiroRepository, AmbienteFinanceiroService ambienteFinanceiroService,
            ContaBancariaRepository contaBancariaRepository,
            CategoriaRepository categoriaRepository, ContatoRepository contatoRepository,
            LancamentoRepository lancamentoRepository, LancamentoRecorrenteRepository lancamentoRecorrenteRepository,
            MovimentacaoRepository movimentacaoRepository, OrcamentoMetaRepository orcamentoMetaRepository,
            EmissorTokenPort emissorTokenPort) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioAmbienteFinanceiroRepository = usuarioAmbienteFinanceiroRepository;
        this.ambienteFinanceiroRepository = ambienteFinanceiroRepository;
        this.ambienteFinanceiroService = ambienteFinanceiroService;
        this.contaBancariaRepository = contaBancariaRepository;
        this.categoriaRepository = categoriaRepository;
        this.contatoRepository = contatoRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.lancamentoRecorrenteRepository = lancamentoRecorrenteRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.orcamentoMetaRepository = orcamentoMetaRepository;
        this.emissorTokenPort = emissorTokenPort;
    }

    /**
     * RNF-09 (decisao consultada com o usuario): exporta o perfil do
     * Usuario + o dump bruto de cada Ambiente Financeiro onde ele e OWNER -
     * ver {@link ExportacaoDadosResumo} para o porque desse escopo.
     */
    public ExportacaoDadosResumo exportarDados(UUID usuarioId) {
        Usuario usuario = localizarUsuarioAtivo(usuarioId);

        List<UsuarioAmbienteFinanceiro> vinculosOwner =
                usuarioAmbienteFinanceiroRepository.findAllByUsuarioIdAndStatus(usuarioId, StatusVinculo.ATIVO).stream()
                        .filter(vinculo -> vinculo.getPerfil() == PerfilUsuarioAmbiente.OWNER)
                        .toList();

        List<AmbienteExportado> ambientes = vinculosOwner.stream().map(this::exportarAmbiente).toList();

        return new ExportacaoDadosResumo(
                new UsuarioExportado(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getCriadoEm()),
                ambientes);
    }

    private AmbienteExportado exportarAmbiente(UsuarioAmbienteFinanceiro vinculo) {
        UUID ambienteId = vinculo.getAmbienteFinanceiroId();
        AmbienteFinanceiro ambiente = ambienteFinanceiroRepository.findById(ambienteId)
                .orElseThrow(() -> new IllegalStateException("Vinculo aponta para um Ambiente Financeiro inexistente: " + ambienteId));

        return new AmbienteExportado(ambiente.getId(), ambiente.getNome(), ambiente.getTipo(),
                contaBancariaRepository.findAllByAmbienteFinanceiroId(ambienteId).stream().map(this::paraResumo).toList(),
                categoriaRepository.findAllByAmbienteFinanceiroId(ambienteId).stream().map(this::paraResumo).toList(),
                contatoRepository.findAllByAmbienteFinanceiroId(ambienteId).stream().map(this::paraResumo).toList(),
                lancamentoRepository.findAllByAmbienteFinanceiroId(ambienteId).stream().map(this::paraResumo).toList(),
                lancamentoRecorrenteRepository.findAllByAmbienteFinanceiroId(ambienteId).stream().map(this::paraResumo).toList(),
                movimentacaoRepository.findAllByAmbienteFinanceiroId(ambienteId).stream().map(this::paraResumo).toList(),
                orcamentoMetaRepository.findAllByAmbienteFinanceiroId(ambienteId).stream().map(this::paraResumo).toList());
    }

    /**
     * RN-13 (decisao consultada com o usuario): soft-delete + anonimizacao
     * (nunca remocao fisica). Para cada vinculo ativo do usuario: se ele e
     * OWNER daquele Ambiente, o Ambiente inteiro e encerrado e todos os
     * vinculos ativos daquele Ambiente sao removidos (nao so o do usuario
     * que saiu); se e so MEMBRO/ADMIN/VISUALIZADOR, so o vinculo dele e
     * removido - o Ambiente e os demais membros continuam intactos. Por
     * fim o proprio Usuario e anonimizado e todos os refresh tokens dele
     * sao revogados, para nenhuma sessao continuar valida.
     *
     * <p>[Bug encontrado ao verificar manualmente contra a aplicacao real]
     * {@code revogarTodosDoUsuario} roda primeiro, nao por ultimo -
     * {@code RefreshTokenRepository.revogarTodosDoUsuario} e um {@code
     * @Modifying(clearAutomatically = true)}, que limpa o contexto de
     * persistencia do Hibernate inteiro ao executar. Chamado depois das
     * mutacoes desta transacao (Usuario/AmbienteFinanceiro/
     * UsuarioAmbienteFinanceiro), essas entidades ja gerenciadas ficavam
     * desanexadas sem nunca terem sido de fato gravadas - o metodo
     * retornava com sucesso, mas nada tinha mudado no banco alem dos
     * refresh tokens. Chamar primeiro evita limpar o contexto antes das
     * outras entidades serem carregadas/alteradas.
     */
    @Transactional
    public void excluirConta(UUID usuarioId) {
        Usuario usuario = localizarUsuarioAtivo(usuarioId);
        emissorTokenPort.revogarTodosDoUsuario(usuarioId);

        List<UsuarioAmbienteFinanceiro> vinculosAtivos =
                usuarioAmbienteFinanceiroRepository.findAllByUsuarioIdAndStatus(usuarioId, StatusVinculo.ATIVO);

        for (UsuarioAmbienteFinanceiro vinculo : vinculosAtivos) {
            if (vinculo.getPerfil() == PerfilUsuarioAmbiente.OWNER) {
                ambienteFinanceiroService.encerrarAmbiente(vinculo.getAmbienteFinanceiroId());
            } else {
                vinculo.remover();
                usuarioAmbienteFinanceiroRepository.save(vinculo);
            }
        }

        usuario.excluir();
        usuarioRepository.save(usuario);
    }

    private Usuario localizarUsuarioAtivo(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("USUARIO_NAO_ENCONTRADO", "Usuario nao encontrado."));
        if (usuario.getStatus() == StatusUsuario.EXCLUIDO) {
            throw new RegraDeNegocioException("USUARIO_JA_EXCLUIDO", "Esta conta ja foi excluida.");
        }
        return usuario;
    }

    private ContaBancariaResumo paraResumo(ContaBancaria conta) {
        return new ContaBancariaResumo(conta.getId(), conta.getNome(), conta.getTipo(), conta.getSaldoInicial(),
                conta.getSaldoAtual(), conta.getMoeda(), conta.isAtiva());
    }

    private CategoriaResumo paraResumo(Categoria categoria) {
        return new CategoriaResumo(categoria.getId(), categoria.getNome(), categoria.getTipo(),
                categoria.getCategoriaPaiId(), categoria.isAtiva(), List.of());
    }

    private ContatoResumo paraResumo(Contato contato) {
        return new ContatoResumo(contato.getId(), contato.getNome(), contato.getTipo(), contato.getDocumento(),
                contato.getEmail(), contato.getTelefone(), contato.isAtivo());
    }

    private LancamentoResumo paraResumo(LancamentoFinanceiro lancamento) {
        return new LancamentoResumo(lancamento.getId(), lancamento.getTipo(), lancamento.getStatus(),
                lancamento.getValor(), lancamento.getDataEmissao(), lancamento.getDataVencimento(),
                lancamento.getDataPagamento(), lancamento.getCategoriaId(), lancamento.getContatoId(),
                lancamento.getContaBancariaId(), lancamento.getFormaPagamento(), lancamento.getOrigem(),
                lancamento.getDescricao(), lancamento.getObservacao(), lancamento.getLinhaDigitavel());
    }

    private LancamentoRecorrenteResumo paraResumo(LancamentoRecorrente recorrente) {
        return new LancamentoRecorrenteResumo(recorrente.getId(), recorrente.getDescricao(), recorrente.getTipo(),
                recorrente.getValor(), recorrente.getCategoriaId(), recorrente.getContaBancariaId(),
                recorrente.getFrequencia(), recorrente.getDiaReferencia(), recorrente.getDataInicio(),
                recorrente.getDataFim(), recorrente.isAtivo());
    }

    private MovimentacaoResumo paraResumo(Movimentacao movimentacao) {
        return new MovimentacaoResumo(movimentacao.getId(), movimentacao.getContaBancariaId(), movimentacao.getTipo(),
                movimentacao.getValor(), movimentacao.getData(), movimentacao.getDescricao(), movimentacao.getOrigem(),
                movimentacao.getCategoriaId(), movimentacao.getLancamentoId(), movimentacao.getGrupoTransferenciaId(),
                movimentacao.getCriadoEm());
    }

    /** {@code valorAtual}/{@code percentualAtingido} ficam nulos - sao progresso calculado sob demanda (RN-10), nao dado armazenado. */
    private OrcamentoMetaResumo paraResumo(OrcamentoMeta orcamentoMeta) {
        return new OrcamentoMetaResumo(orcamentoMeta.getId(), orcamentoMeta.getTipo(), orcamentoMeta.getCategoriaId(),
                orcamentoMeta.getNome(), orcamentoMeta.getValorLimite(), orcamentoMeta.getValorAlvo(),
                orcamentoMeta.getPeriodo(), orcamentoMeta.getDataInicio(), orcamentoMeta.getDataFim(),
                orcamentoMeta.getContaBancariaId(), orcamentoMeta.getStatus(), null, null,
                orcamentoMeta.getLimiarAlertaPercentual());
    }
}
