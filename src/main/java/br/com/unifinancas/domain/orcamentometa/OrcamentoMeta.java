package br.com.unifinancas.domain.orcamentometa;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Limite de gasto planejado por categoria/periodo (ORCAMENTO), ou meta de
 * economia acompanhada ao longo do tempo (META) - RF-12, RF-13, SAD Sec4.11.
 * {@code categoriaId}/{@code valorLimite} sao exclusivos de ORCAMENTO;
 * {@code nome}/{@code valorAlvo}/{@code contaBancariaId} sao exclusivos de
 * META - por isso duas factories distintas, cada uma so aceitando os campos
 * que fazem sentido para aquele tipo (estado invalido nao e representavel).
 * RN-10: o progresso NAO mora aqui - e calculado sob demanda a partir dos
 * Lancamentos (OrcamentoMetaService, T1.4.2).
 *
 * <p>T1.5.3, Fluxo 6.4: {@code limiarAlertaPercentual} e o limiar
 * configuravel de "aviso" (decisao consultada com o usuario - o de
 * "estourado" e fixo em 100%, a propria definicao de exceder o limite, sem
 * sentido ser configuravel). {@code ultimoAlertaTipo}/{@code ultimoAlertaPeriodo}
 * evitam notificar repetidamente o mesmo limiar dentro do mesmo periodo.
 */
@Entity
@Table(name = "orcamento_meta")
public class OrcamentoMeta {

    private static final BigDecimal LIMIAR_ALERTA_PADRAO = new BigDecimal("80");
    private static final BigDecimal CEM = new BigDecimal("100");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoOrcamentoMeta tipo;

    @Column(name = "categoria_id")
    private UUID categoriaId;

    @Column(length = 100)
    private String nome;

    @Column(name = "valor_limite", precision = 15, scale = 2)
    private BigDecimal valorLimite;

    @Column(name = "valor_alvo", precision = 15, scale = 2)
    private BigDecimal valorAlvo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PeriodoOrcamentoMeta periodo;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "conta_bancaria_id")
    private UUID contaBancariaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusOrcamentoMeta status;

    @Column(name = "limiar_alerta_percentual", nullable = false, precision = 5, scale = 2)
    private BigDecimal limiarAlertaPercentual;

    @Enumerated(EnumType.STRING)
    @Column(name = "ultimo_alerta_tipo", length = 20)
    private SeveridadeAlertaOrcamento ultimoAlertaTipo;

    @Column(name = "ultimo_alerta_periodo")
    private LocalDate ultimoAlertaPeriodo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected OrcamentoMeta() {
        // exigido pelo JPA
    }

    /**
     * RF-12: orcamento mensal por categoria - categoriaId e valorLimite (> 0)
     * sao obrigatorios. {@code limiarAlertaPercentual} e opcional (nulo usa
     * o padrao de 80%) - se informado, precisa estar entre 1 e 100.
     */
    public static OrcamentoMeta novoOrcamento(UUID ambienteFinanceiroId, UUID categoriaId, BigDecimal valorLimite,
            BigDecimal limiarAlertaPercentual, LocalDate dataInicio, LocalDate dataFim) {
        if (categoriaId == null) {
            throw new RegraDeNegocioException(
                    "CATEGORIA_OBRIGATORIA_PARA_ORCAMENTO", "Orcamento exige uma categoria.");
        }
        validarValorPositivo(valorLimite, "VALOR_LIMITE_OBRIGATORIO_PARA_ORCAMENTO",
                "valorLimite e obrigatorio e deve ser maior que zero para Orcamento.");

        OrcamentoMeta orcamentoMeta = base(ambienteFinanceiroId, dataInicio, dataFim);
        orcamentoMeta.tipo = TipoOrcamentoMeta.ORCAMENTO;
        orcamentoMeta.categoriaId = categoriaId;
        orcamentoMeta.valorLimite = valorLimite;
        orcamentoMeta.limiarAlertaPercentual = validarLimiarAlerta(limiarAlertaPercentual);
        return orcamentoMeta;
    }

    /** RF-13: meta de economia - valorAlvo (> 0) e obrigatorio; contaBancariaId e nome sao opcionais. */
    public static OrcamentoMeta novaMeta(UUID ambienteFinanceiroId, String nome, BigDecimal valorAlvo,
            UUID contaBancariaId, LocalDate dataInicio, LocalDate dataFim) {
        validarValorPositivo(valorAlvo, "VALOR_ALVO_OBRIGATORIO_PARA_META",
                "valorAlvo e obrigatorio e deve ser maior que zero para Meta.");

        OrcamentoMeta orcamentoMeta = base(ambienteFinanceiroId, dataInicio, dataFim);
        orcamentoMeta.tipo = TipoOrcamentoMeta.META;
        orcamentoMeta.nome = nome;
        orcamentoMeta.valorAlvo = valorAlvo;
        orcamentoMeta.contaBancariaId = contaBancariaId;
        return orcamentoMeta;
    }

    private static OrcamentoMeta base(UUID ambienteFinanceiroId, LocalDate dataInicio, LocalDate dataFim) {
        OrcamentoMeta orcamentoMeta = new OrcamentoMeta();
        orcamentoMeta.ambienteFinanceiroId = ambienteFinanceiroId;
        orcamentoMeta.periodo = PeriodoOrcamentoMeta.MENSAL;
        orcamentoMeta.dataInicio = dataInicio;
        orcamentoMeta.dataFim = dataFim;
        orcamentoMeta.status = StatusOrcamentoMeta.ATIVO;
        orcamentoMeta.limiarAlertaPercentual = LIMIAR_ALERTA_PADRAO;
        Instant agora = Instant.now();
        orcamentoMeta.criadoEm = agora;
        orcamentoMeta.atualizadoEm = agora;
        return orcamentoMeta;
    }

    private static void validarValorPositivo(BigDecimal valor, String codigo, String mensagem) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException(codigo, mensagem);
        }
    }

    private static BigDecimal validarLimiarAlerta(BigDecimal limiarAlertaPercentual) {
        if (limiarAlertaPercentual == null) {
            return LIMIAR_ALERTA_PADRAO;
        }
        if (limiarAlertaPercentual.compareTo(BigDecimal.ZERO) <= 0 || limiarAlertaPercentual.compareTo(CEM) > 0) {
            throw new RegraDeNegocioException(
                    "LIMIAR_ALERTA_INVALIDO", "limiarAlertaPercentual deve estar entre 1 e 100.");
        }
        return limiarAlertaPercentual;
    }

    /**
     * T1.5.3, Fluxo 6.4: evita notificar repetidamente o mesmo limiar dentro
     * do mesmo periodo - so dispara de novo se a severidade mudou (ex.: de
     * AVISO para ESTOURADO) ou se o periodo avancou (novo mes).
     */
    public boolean jaAlertouNestePeriodo(SeveridadeAlertaOrcamento severidade, LocalDate inicioPeriodo) {
        return severidade == this.ultimoAlertaTipo && inicioPeriodo.equals(this.ultimoAlertaPeriodo);
    }

    public void registrarAlertaEnviado(SeveridadeAlertaOrcamento severidade, LocalDate inicioPeriodo) {
        this.ultimoAlertaTipo = severidade;
        this.ultimoAlertaPeriodo = inicioPeriodo;
        this.atualizadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAmbienteFinanceiroId() {
        return ambienteFinanceiroId;
    }

    public TipoOrcamentoMeta getTipo() {
        return tipo;
    }

    public UUID getCategoriaId() {
        return categoriaId;
    }

    public String getNome() {
        return nome;
    }

    public BigDecimal getValorLimite() {
        return valorLimite;
    }

    public BigDecimal getValorAlvo() {
        return valorAlvo;
    }

    public PeriodoOrcamentoMeta getPeriodo() {
        return periodo;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public UUID getContaBancariaId() {
        return contaBancariaId;
    }

    public StatusOrcamentoMeta getStatus() {
        return status;
    }

    public BigDecimal getLimiarAlertaPercentual() {
        return limiarAlertaPercentual;
    }

    public SeveridadeAlertaOrcamento getUltimoAlertaTipo() {
        return ultimoAlertaTipo;
    }

    public LocalDate getUltimoAlertaPeriodo() {
        return ultimoAlertaPeriodo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
