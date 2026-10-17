package br.com.unifinancas.domain.fatura;

import br.com.unifinancas.domain.cartao.Cartao;
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
import java.time.YearMonth;
import java.util.UUID;

/**
 * Consolidacao das compras de um Cartao num ciclo/competencia (RF-17, SAD
 * Sec4.6). Ciclo de vida em Sec6.5: ABERTA -> FECHADA -> {PAGA, PAGA_PARCIAL,
 * ATRASADA} -> ... -> PAGA (terminal). Sem {@code ambienteFinanceiroId}
 * proprio (Sec5.2) - isolamento por tenant passa por JOIN com {@code Cartao}
 * (ver {@code FaturaRepository}).
 */
@Entity
@Table(name = "fatura")
public class Fatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "cartao_id", nullable = false)
    private UUID cartaoId;

    /** Primeiro dia do mes de referencia (Sec4.6). */
    @Column(nullable = false)
    private LocalDate competencia;

    @Column(name = "data_fechamento", nullable = false)
    private LocalDate dataFechamento;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "valor_pago", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusFatura status;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Fatura() {
        // exigido pelo JPA
    }

    /** Nasce sempre ABERTA, valorTotal/valorPago zerados - T2.1.2 ({@code CartaoService}) cria uma por competencia. */
    public static Fatura novo(UUID cartaoId, LocalDate competencia, LocalDate dataFechamento, LocalDate dataVencimento) {
        Fatura fatura = new Fatura();
        fatura.cartaoId = cartaoId;
        fatura.competencia = competencia;
        fatura.dataFechamento = dataFechamento;
        fatura.dataVencimento = dataVencimento;
        fatura.valorTotal = BigDecimal.ZERO;
        fatura.valorPago = BigDecimal.ZERO;
        fatura.status = StatusFatura.ABERTA;
        Instant agora = Instant.now();
        fatura.criadoEm = agora;
        fatura.atualizadoEm = agora;
        return fatura;
    }

    /**
     * T2.1.2: cria a Fatura de uma competencia a partir das datas do
     * Cartao (diaFechamento/diaVencimento) - usado tanto na criacao do
     * Cartao (primeira Fatura, competencia = mes corrente) quanto pelo job
     * de fechamento (a Fatura da competencia seguinte). Se
     * {@code diaVencimento <= diaFechamento} (padrao comum: fecha dia 25,
     * vence dia 5), o vencimento cai no mes SEGUINTE ao da competencia -
     * senao, no mesmo mes. Dia ajustado para o ultimo valido do mes quando
     * o mes e mais curto (ex.: dia 31 em fevereiro), mesmo criterio ja
     * usado por {@code LancamentoRecorrente.ocorreEm} (frequencia MENSAL).
     */
    public static Fatura novoParaCompetencia(Cartao cartao, YearMonth competencia) {
        LocalDate dataFechamento = diaAjustado(competencia, cartao.getDiaFechamento());
        YearMonth mesVencimento = cartao.getDiaVencimento() > cartao.getDiaFechamento() ? competencia : competencia.plusMonths(1);
        LocalDate dataVencimento = diaAjustado(mesVencimento, cartao.getDiaVencimento());
        return novo(cartao.getId(), competencia.atDay(1), dataFechamento, dataVencimento);
    }

    private static LocalDate diaAjustado(YearMonth mes, int dia) {
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }

    /**
     * T2.2 (Compra, fora de escopo da T2.1): acumula o valor de uma compra
     * lancada nesta Fatura - so permitido enquanto ABERTA (uma Fatura
     * fechada nao recebe compra nova, ela cai na competencia seguinte).
     */
    public void adicionarValor(BigDecimal valor) {
        if (status != StatusFatura.ABERTA) {
            throw new RegraDeNegocioException(
                    "FATURA_NAO_ABERTA", "So e possivel adicionar valor a uma Fatura ABERTA.");
        }
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException("VALOR_INVALIDO", "O valor a adicionar deve ser maior que zero.");
        }
        this.valorTotal = this.valorTotal.add(valor);
        this.atualizadoEm = Instant.now();
    }

    /** Sec6.5: ABERTA -> FECHADA, quando o dia de fechamento do Cartao e atingido (job, T2.1.2). */
    public void fechar() {
        if (status != StatusFatura.ABERTA) {
            throw new RegraDeNegocioException("TRANSICAO_INVALIDA", "So e possivel fechar uma Fatura ABERTA.");
        }
        this.status = StatusFatura.FECHADA;
        this.atualizadoEm = Instant.now();
    }

    /** Sec6.5: FECHADA -> ATRASADA, quando dataVencimento ja passou sem pagamento algum (job, T2.1.2). */
    public void marcarComoAtrasada() {
        if (status != StatusFatura.FECHADA) {
            throw new RegraDeNegocioException(
                    "TRANSICAO_INVALIDA", "So e possivel marcar como atrasada uma Fatura FECHADA.");
        }
        this.status = StatusFatura.ATRASADA;
        this.atualizadoEm = Instant.now();
    }

    /**
     * Sec6.5, RN-11: paga total ou parcialmente - permitido em FECHADA,
     * ATRASADA ou PAGA_PARCIAL (varios pagamentos parciais sucessivos ate
     * quitar). {@code valor} maior que o saldo remanescente lanca
     * regra de negocio (a UI/servico deve limitar o valor antes de chamar -
     * RN-11 nao preve pagar mais do que se deve).
     */
    public void pagar(BigDecimal valor) {
        if (status != StatusFatura.FECHADA && status != StatusFatura.ATRASADA && status != StatusFatura.PAGA_PARCIAL) {
            throw new RegraDeNegocioException("TRANSICAO_INVALIDA",
                    "So e possivel pagar uma Fatura FECHADA, ATRASADA ou PAGA_PARCIAL.");
        }
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException("VALOR_INVALIDO", "O valor do pagamento deve ser maior que zero.");
        }
        if (valor.compareTo(saldoRemanescente()) > 0) {
            throw new RegraDeNegocioException(
                    "VALOR_MAIOR_QUE_SALDO", "O valor do pagamento nao pode ser maior que o saldo remanescente.");
        }

        this.valorPago = this.valorPago.add(valor);
        this.status = saldoRemanescente().compareTo(BigDecimal.ZERO) == 0 ? StatusFatura.PAGA : StatusFatura.PAGA_PARCIAL;
        this.atualizadoEm = Instant.now();
    }

    /** RN-11: usado por quem calcula o juros rotativo e o valor de cada parcela do parcelamento. */
    public BigDecimal saldoRemanescente() {
        return valorTotal.subtract(valorPago);
    }

    /** Sec6.5, RN-11: so uma Fatura ATRASADA pode ser parcelada. */
    public boolean podeSerParcelada() {
        return status == StatusFatura.ATRASADA;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCartaoId() {
        return cartaoId;
    }

    public LocalDate getCompetencia() {
        return competencia;
    }

    public LocalDate getDataFechamento() {
        return dataFechamento;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public BigDecimal getValorPago() {
        return valorPago;
    }

    public StatusFatura getStatus() {
        return status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
