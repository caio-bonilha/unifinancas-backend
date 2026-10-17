package br.com.unifinancas.domain.lancamentorecorrente;

import br.com.unifinancas.domain.lancamento.TipoLancamento;
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
 * Regra que origina Lancamentos futuros (RF-11, SAD Sec4.13). Mapeia a
 * tabela {@code lancamento_recorrente} (T1.6.1) por inteiro. Apenas a
 * entidade de dominio e suas regras nesta tarefa - repositorio e servico de
 * CRUD ficam para a T1.6.2, e o calculo de proxima ocorrencia/materializacao
 * em Lancamento para o {@code RecorrenciaJob} (T1.6.3, Fluxo 6.6), mesmo
 * padrao ja usado para Lancamento Financeiro (T1.3.1/T1.3.2/T1.3.3).
 *
 * <p>[Revisado - ADR-012] {@code contaBancariaDestinoId} e o suporte a
 * TRANSFERENCIA recorrente foram removidos: transferencia deixou de ser um
 * Lancamento (RN-05), entao nao ha mais "Lancamento Recorrente de
 * Transferencia" - agendamento de transferencia recorrente fica fora de
 * escopo por ora (consequencia documentada no ADR-012).
 */
@Entity
@Table(name = "lancamento_recorrente")
public class LancamentoRecorrente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    @Column(nullable = false, length = 150)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoLancamento tipo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "categoria_id")
    private UUID categoriaId;

    @Column(name = "conta_bancaria_id")
    private UUID contaBancariaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FrequenciaRecorrencia frequencia;

    /** T1.6.3: dia da semana ISO (SEMANAL) ou dia do mes (MENSAL) - obrigatorio nessas duas frequencias, ignorado em DIARIA/ANUAL. Ver {@link #ocorreEm}. */
    @Column(name = "dia_referencia")
    private Integer diaReferencia;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected LancamentoRecorrente() {
        // exigido pelo JPA
    }

    /** Nasce sempre {@code ativo}. Valida as mesmas invariantes de {@link br.com.unifinancas.domain.lancamento.LancamentoFinanceiro#novo}. */
    public static LancamentoRecorrente novo(UUID ambienteFinanceiroId, String descricao, TipoLancamento tipo,
            BigDecimal valor, UUID categoriaId, UUID contaBancariaId,
            FrequenciaRecorrencia frequencia, Integer diaReferencia, LocalDate dataInicio, LocalDate dataFim) {
        validarDescricao(descricao);
        validarValor(valor);
        validarPeriodo(dataInicio, dataFim);
        validarDiaReferencia(frequencia, diaReferencia);

        LancamentoRecorrente recorrente = new LancamentoRecorrente();
        recorrente.ambienteFinanceiroId = ambienteFinanceiroId;
        recorrente.descricao = descricao;
        recorrente.tipo = tipo;
        recorrente.valor = valor;
        recorrente.categoriaId = categoriaId;
        recorrente.contaBancariaId = contaBancariaId;
        recorrente.frequencia = frequencia;
        recorrente.diaReferencia = diaReferencia;
        recorrente.dataInicio = dataInicio;
        recorrente.dataFim = dataFim;
        recorrente.ativo = true;
        Instant agora = Instant.now();
        recorrente.criadoEm = agora;
        recorrente.atualizadoEm = agora;
        return recorrente;
    }

    /**
     * T1.6.2: edita os campos descritivos - {@code tipo} nunca muda depois
     * de criado (mesma razao de {@link
     * br.com.unifinancas.domain.lancamento.LancamentoFinanceiro#editar}:
     * trocar DESPESA por RECEITA nao faz sentido; desativar e criar de novo
     * e o caminho certo). Reaplica a validacao de periodo, exatamente como
     * na criacao.
     */
    public void editar(String descricao, BigDecimal valor, UUID categoriaId, UUID contaBancariaId,
            FrequenciaRecorrencia frequencia, Integer diaReferencia, LocalDate dataInicio, LocalDate dataFim) {
        validarDescricao(descricao);
        validarValor(valor);
        validarPeriodo(dataInicio, dataFim);
        validarDiaReferencia(frequencia, diaReferencia);

        this.descricao = descricao;
        this.valor = valor;
        this.categoriaId = categoriaId;
        this.contaBancariaId = contaBancariaId;
        this.frequencia = frequencia;
        this.diaReferencia = diaReferencia;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.atualizadoEm = Instant.now();
    }

    /** T1.6.2: idempotente - pausa a geracao de novos Lancamentos (RecorrenciaJob, T1.6.3, so materializa recorrencias ativas). */
    public void desativar() {
        this.ativo = false;
        this.atualizadoEm = Instant.now();
    }

    /** T1.6.2: idempotente - retoma a geracao de novos Lancamentos. */
    public void reativar() {
        this.ativo = true;
        this.atualizadoEm = Instant.now();
    }

    private static void validarDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            throw new RegraDeNegocioException("DESCRICAO_OBRIGATORIA", "descricao e obrigatoria.");
        }
    }

    private static void validarValor(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException("VALOR_INVALIDO", "O valor do lancamento recorrente deve ser maior que zero.");
        }
    }

    private static void validarPeriodo(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null) {
            throw new RegraDeNegocioException("DATA_INICIO_OBRIGATORIA", "dataInicio e obrigatoria.");
        }
        if (dataFim != null && dataFim.isBefore(dataInicio)) {
            throw new RegraDeNegocioException("PERIODO_INVALIDO", "dataFim nao pode ser anterior a dataInicio.");
        }
    }

    /**
     * T1.6.3, decisao consultada com o usuario: diaReferencia so faz
     * sentido (e e obrigatorio) para SEMANAL (dia da semana ISO, 1=segunda
     * a 7=domingo) e MENSAL (dia do mes, 1-31, ajustado para o ultimo dia
     * valido em meses mais curtos - {@link #ocorreEm}). DIARIA ocorre todo
     * dia e ANUAL usa o aniversario de {@code dataInicio}; nenhuma das duas
     * usa diaReferencia.
     */
    private static void validarDiaReferencia(FrequenciaRecorrencia frequencia, Integer diaReferencia) {
        switch (frequencia) {
            case SEMANAL -> {
                if (diaReferencia == null || diaReferencia < 1 || diaReferencia > 7) {
                    throw new RegraDeNegocioException("DIA_REFERENCIA_INVALIDO",
                            "diaReferencia deve estar entre 1 (segunda) e 7 (domingo) para frequencia SEMANAL.");
                }
            }
            case MENSAL -> {
                if (diaReferencia == null || diaReferencia < 1 || diaReferencia > 31) {
                    throw new RegraDeNegocioException("DIA_REFERENCIA_INVALIDO",
                            "diaReferencia deve estar entre 1 e 31 para frequencia MENSAL.");
                }
            }
            case DIARIA, ANUAL -> {
                // diaReferencia nao se aplica - DIARIA ocorre todo dia, ANUAL usa o aniversario de dataInicio.
            }
        }
    }

    /**
     * T1.6.3, Fluxo 6.6: verdadeiro se esta recorrencia (ativa ou nao - quem
     * chama decide se filtra por {@link #isAtivo()}) tem uma ocorrencia
     * exatamente na data informada, respeitando {@code dataInicio}/{@code
     * dataFim}. Usado pelo {@code RecorrenciaJob} para decidir se materializa
     * um Lancamento hoje.
     */
    public boolean ocorreEm(LocalDate data) {
        if (data.isBefore(dataInicio)) {
            return false;
        }
        if (dataFim != null && data.isAfter(dataFim)) {
            return false;
        }
        return switch (frequencia) {
            case DIARIA -> true;
            case SEMANAL -> data.getDayOfWeek().getValue() == diaReferencia;
            case MENSAL -> data.getDayOfMonth() == Math.min(diaReferencia, data.lengthOfMonth());
            case ANUAL -> ocorreNoAniversario(data);
        };
    }

    /** Aniversario de dataInicio (mesmo mes/dia) - dia ajustado para o ultimo valido do mes em anos sem 29/02 (SAD nao especifica, decisao tecnica desta tarefa). */
    private boolean ocorreNoAniversario(LocalDate data) {
        int ultimoDiaDoMesNoAno = LocalDate.of(data.getYear(), dataInicio.getMonth(), 1).lengthOfMonth();
        int diaEfetivo = Math.min(dataInicio.getDayOfMonth(), ultimoDiaDoMesNoAno);
        return data.getMonth() == dataInicio.getMonth() && data.getDayOfMonth() == diaEfetivo;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAmbienteFinanceiroId() {
        return ambienteFinanceiroId;
    }

    public String getDescricao() {
        return descricao;
    }

    public TipoLancamento getTipo() {
        return tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public UUID getCategoriaId() {
        return categoriaId;
    }

    public UUID getContaBancariaId() {
        return contaBancariaId;
    }

    public FrequenciaRecorrencia getFrequencia() {
        return frequencia;
    }

    public Integer getDiaReferencia() {
        return diaReferencia;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
