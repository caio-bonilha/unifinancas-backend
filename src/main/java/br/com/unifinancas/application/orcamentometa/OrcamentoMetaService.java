package br.com.unifinancas.application.orcamentometa;

import br.com.unifinancas.application.notificacao.NotificacaoService;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.StatusVinculo;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import br.com.unifinancas.domain.categoria.Categoria;
import br.com.unifinancas.domain.categoria.CategoriaRepository;
import br.com.unifinancas.domain.contabancaria.ContaBancaria;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.lancamento.LancamentoCriadoEvent;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.notificacao.CanalNotificacao;
import br.com.unifinancas.domain.notificacao.TipoNotificacao;
import br.com.unifinancas.domain.orcamentometa.OrcamentoMeta;
import br.com.unifinancas.domain.orcamentometa.OrcamentoMetaRepository;
import br.com.unifinancas.domain.orcamentometa.SeveridadeAlertaOrcamento;
import br.com.unifinancas.domain.orcamentometa.StatusOrcamentoMeta;
import br.com.unifinancas.domain.orcamentometa.TipoOrcamentoMeta;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * RF-12/RF-13, SAD Sec4.11, Sec9.2: CRUD de Orcamento/Meta, calculo de
 * progresso sob demanda (RN-10), e reacao a {@code LancamentoCriadoEvent}
 * para disparar alerta de limiar (Fluxo 6.4, T1.5.3) - referencia
 * {@code lancamento}, {@code contabancaria} e {@code ambientefinanceiro}
 * so pelas interfaces publicas necessarias, sem escrita adicional em
 * orcamento_meta para guardar o progresso em si (RN-10).
 */
@Service
public class OrcamentoMetaService {

    private static final int ESCALA_PERCENTUAL = 2;
    private static final BigDecimal CEM = new BigDecimal("100");

    private final OrcamentoMetaRepository orcamentoMetaRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContaBancariaRepository contaBancariaRepository;
    private final LancamentoRepository lancamentoRepository;
    private final UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository;
    private final NotificacaoService notificacaoService;

    public OrcamentoMetaService(OrcamentoMetaRepository orcamentoMetaRepository, CategoriaRepository categoriaRepository,
            ContaBancariaRepository contaBancariaRepository, LancamentoRepository lancamentoRepository,
            UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository, NotificacaoService notificacaoService) {
        this.orcamentoMetaRepository = orcamentoMetaRepository;
        this.categoriaRepository = categoriaRepository;
        this.contaBancariaRepository = contaBancariaRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.usuarioAmbienteFinanceiroRepository = usuarioAmbienteFinanceiroRepository;
        this.notificacaoService = notificacaoService;
    }

    /** SAD Sec8.2: leitura de dados financeiros e permitida a qualquer perfil, inclusive VISUALIZADOR. */
    public List<OrcamentoMetaResumo> listarComProgresso(TenantContext contexto) {
        return orcamentoMetaRepository.findAllByAmbienteFinanceiroId(contexto.ambienteFinanceiroId()).stream()
                .map(this::paraResumo)
                .toList();
    }

    /**
     * SAD Sec8.2: "Criar/editar Orcamento/Meta" exige OWNER, ADMIN ou MEMBRO.
     * {@code limiarAlertaPercentual} e opcional (T1.5.3, Fluxo 6.4) - nulo
     * usa o padrao de 80% (validado na propria entidade).
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public OrcamentoMetaResumo criarOrcamento(TenantContext contexto, UUID categoriaId, BigDecimal valorLimite,
            BigDecimal limiarAlertaPercentual, LocalDate dataInicio, LocalDate dataFim) {
        if (categoriaId != null) {
            validarCategoriaDoAmbiente(contexto.ambienteFinanceiroId(), categoriaId);
        }

        OrcamentoMeta orcamento = OrcamentoMeta.novoOrcamento(
                contexto.ambienteFinanceiroId(), categoriaId, valorLimite, limiarAlertaPercentual, dataInicio, dataFim);
        orcamentoMetaRepository.save(orcamento);
        return paraResumo(orcamento);
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public OrcamentoMetaResumo criarMeta(TenantContext contexto, String nome, BigDecimal valorAlvo,
            UUID contaBancariaId, LocalDate dataInicio, LocalDate dataFim) {
        if (contaBancariaId != null) {
            validarContaBancariaDoAmbiente(contexto.ambienteFinanceiroId(), contaBancariaId);
        }

        OrcamentoMeta meta = OrcamentoMeta.novaMeta(
                contexto.ambienteFinanceiroId(), nome, valorAlvo, contaBancariaId, dataInicio, dataFim);
        orcamentoMetaRepository.save(meta);
        return paraResumo(meta);
    }

    /**
     * Fluxo 6.4, T1.5.3: a cada Lancamento DESPESA criado, reavalia o
     * progresso dos Orcamentos ATIVOs daquela Categoria e dispara alerta
     * (AVISO ou ESTOURADO) para os membros ativos do ambiente, uma unica vez
     * por limiar/periodo (decisao consultada com o usuario - evita spam a
     * cada novo lancamento dentro do mesmo limiar). AFTER_COMMIT, como
     * AuditoriaService (T1.3.7): reacao a evento, nao faz parte da
     * transacao que criou o Lancamento (Sec3.4/Sec13.5).
     */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoLancamentoCriado(LancamentoCriadoEvent evento) {
        if (evento.tipo() != TipoLancamento.DESPESA || evento.categoriaId() == null) {
            return;
        }

        List<OrcamentoMeta> orcamentos = orcamentoMetaRepository.findAllByAmbienteFinanceiroIdAndCategoriaIdAndTipoAndStatus(
                evento.ambienteFinanceiroId(), evento.categoriaId(), TipoOrcamentoMeta.ORCAMENTO, StatusOrcamentoMeta.ATIVO);
        for (OrcamentoMeta orcamento : orcamentos) {
            avaliarLimiar(orcamento);
        }
    }

    private void avaliarLimiar(OrcamentoMeta orcamento) {
        LocalDate inicioPeriodo = LocalDate.now().withDayOfMonth(1);
        BigDecimal valorAtual = calcularGastoDoMesVigente(orcamento);
        BigDecimal percentual = calcularPercentual(valorAtual, orcamento.getValorLimite());
        if (percentual == null) {
            return;
        }

        SeveridadeAlertaOrcamento severidade;
        if (percentual.compareTo(CEM) >= 0) {
            severidade = SeveridadeAlertaOrcamento.ESTOURADO;
        } else if (percentual.compareTo(orcamento.getLimiarAlertaPercentual()) >= 0) {
            severidade = SeveridadeAlertaOrcamento.AVISO;
        } else {
            return;
        }

        if (orcamento.jaAlertouNestePeriodo(severidade, inicioPeriodo)) {
            return;
        }

        notificarMembros(orcamento, severidade, valorAtual, percentual);
        orcamento.registrarAlertaEnviado(severidade, inicioPeriodo);
        orcamentoMetaRepository.save(orcamento);
    }

    /** Fluxo 6.4: "notificacao multicanal para os membros do Ambiente Financeiro com permissao de visualizacao" - todos os ativos (Sec12.3: leitura e universal). */
    private void notificarMembros(OrcamentoMeta orcamento, SeveridadeAlertaOrcamento severidade, BigDecimal valorAtual,
            BigDecimal percentual) {
        String nomeCategoria = categoriaRepository.findByIdAndAmbienteFinanceiroId(
                        orcamento.getCategoriaId(), orcamento.getAmbienteFinanceiroId())
                .map(Categoria::getNome)
                .orElse("categoria removida");
        String titulo = severidade == SeveridadeAlertaOrcamento.ESTOURADO
                ? "Orcamento estourado: " + nomeCategoria
                : "Orcamento perto do limite: " + nomeCategoria;
        String mensagem = String.format(
                "O orcamento de %s atingiu %s%% do limite (R$ %s de R$ %s).",
                nomeCategoria, percentual, valorAtual, orcamento.getValorLimite());

        List<UsuarioAmbienteFinanceiro> membrosAtivos = usuarioAmbienteFinanceiroRepository
                .findAllByAmbienteFinanceiroIdAndStatus(orcamento.getAmbienteFinanceiroId(), StatusVinculo.ATIVO);
        for (UsuarioAmbienteFinanceiro membro : membrosAtivos) {
            notificacaoService.criar(orcamento.getAmbienteFinanceiroId(), membro.getUsuarioId(),
                    TipoNotificacao.ORCAMENTO_LIMITE, titulo, mensagem, "orcamento_meta", orcamento.getId(),
                    Set.of(CanalNotificacao.PUSH, CanalNotificacao.EMAIL));
        }
    }

    private void validarCategoriaDoAmbiente(UUID ambienteFinanceiroId, UUID categoriaId) {
        categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CATEGORIA_NAO_ENCONTRADA", "Categoria nao encontrada neste Ambiente Financeiro."));
    }

    private void validarContaBancariaDoAmbiente(UUID ambienteFinanceiroId, UUID contaBancariaId) {
        contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteFinanceiroId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CONTA_BANCARIA_NAO_ENCONTRADA", "Conta Bancaria nao encontrada neste Ambiente Financeiro."));
    }

    private OrcamentoMetaResumo paraResumo(OrcamentoMeta orcamentoMeta) {
        BigDecimal valorAtual;
        BigDecimal valorReferencia;
        if (orcamentoMeta.getTipo() == TipoOrcamentoMeta.ORCAMENTO) {
            valorAtual = calcularGastoDoMesVigente(orcamentoMeta);
            valorReferencia = orcamentoMeta.getValorLimite();
        } else {
            valorAtual = calcularSaldoDaContaVinculada(orcamentoMeta);
            valorReferencia = orcamentoMeta.getValorAlvo();
        }

        return new OrcamentoMetaResumo(orcamentoMeta.getId(), orcamentoMeta.getTipo(), orcamentoMeta.getCategoriaId(),
                orcamentoMeta.getNome(), orcamentoMeta.getValorLimite(), orcamentoMeta.getValorAlvo(),
                orcamentoMeta.getPeriodo(), orcamentoMeta.getDataInicio(), orcamentoMeta.getDataFim(),
                orcamentoMeta.getContaBancariaId(), orcamentoMeta.getStatus(), valorAtual,
                calcularPercentual(valorAtual, valorReferencia), orcamentoMeta.getLimiarAlertaPercentual());
    }

    /** RN-10: soma dos Lancamentos DESPESA da Categoria no mes corrente (periodo MENSAL, unico suportado). */
    private BigDecimal calcularGastoDoMesVigente(OrcamentoMeta orcamento) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        LocalDate fimMes = hoje.withDayOfMonth(hoje.lengthOfMonth());
        return lancamentoRepository.somarDespesasPorCategoriaEPeriodo(
                orcamento.getAmbienteFinanceiroId(), orcamento.getCategoriaId(), inicioMes, fimMes);
    }

    /**
     * Progresso de uma Meta = saldo derivado da Conta Bancaria vinculada
     * (Sec4.4) - so calculavel se a Meta tiver uma conta vinculada; sem
     * isso nao ha nenhuma outra fonte de dado que sustente o progresso.
     */
    private BigDecimal calcularSaldoDaContaVinculada(OrcamentoMeta meta) {
        if (meta.getContaBancariaId() == null) {
            return null;
        }
        return contaBancariaRepository
                .findByIdAndAmbienteFinanceiroId(meta.getContaBancariaId(), meta.getAmbienteFinanceiroId())
                .map(ContaBancaria::getSaldoAtual)
                .orElse(null);
    }

    private BigDecimal calcularPercentual(BigDecimal valorAtual, BigDecimal valorReferencia) {
        if (valorAtual == null || valorReferencia == null || valorReferencia.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return valorAtual.divide(valorReferencia, ESCALA_PERCENTUAL + 2, RoundingMode.HALF_UP)
                .multiply(CEM)
                .setScale(ESCALA_PERCENTUAL, RoundingMode.HALF_UP);
    }
}
