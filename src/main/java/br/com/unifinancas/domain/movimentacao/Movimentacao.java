package br.com.unifinancas.domain.movimentacao;

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
 * ADR-012, SAD Sec4.4a: razao da Conta Bancaria - unica fonte do
 * {@code saldoAtual} (RN-12), distinta do Lancamento Financeiro (a
 * obrigacao a pagar/receber). Imutavel apos criada - nao existe metodo de
 * edicao; so nasce de duas formas: (1) {@code LancamentoService.confirmarPagamento}/
 * {@code confirmarRecebimento}, vinculada ao Lancamento liquidado via
 * {@link #paraLancamento}, ou (2) uma Transferencia, sempre em par (debito
 * na origem, credito no destino) via {@link #paraTransferencia}, sem
 * Lancamento algum.
 */
@Entity
@Table(name = "movimentacao_conta_bancaria")
public class Movimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    @Column(name = "conta_bancaria_id", nullable = false)
    private UUID contaBancariaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMovimentacao tipo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, length = 200)
    private String descricao;

    /** ADR-014: explicita de onde a Movimentacao nasceu - determina mutabilidade (ver Javadoc de {@link OrigemMovimentacao}). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OrigemMovimentacao origem;

    /** ADR-014: opcional, so preenchido quando {@code origem=MANUAL} - Movimentacao de Lancamento/Transferencia nao tem categoria propria. */
    @Column(name = "categoria_id")
    private UUID categoriaId;

    /** Preenchido quando a Movimentacao nasce da liquidacao de um Lancamento (RN-12); nulo para Transferencia. */
    @Column(name = "lancamento_id")
    private UUID lancamentoId;

    /** Preenchido apenas em Movimentacoes de Transferencia - identifica as duas pernas do mesmo par (ADR-012). */
    @Column(name = "grupo_transferencia_id")
    private UUID grupoTransferenciaId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Movimentacao() {
        // exigido pelo JPA
    }

    /**
     * RN-12: cria a Movimentacao vinculada a um Lancamento sendo liquidado -
     * {@code LancamentoService.confirmarPagamento} (DEBITO) e
     * {@code confirmarRecebimento} (CREDITO) sao os unicos chamadores.
     */
    public static Movimentacao paraLancamento(UUID ambienteFinanceiroId, UUID contaBancariaId, TipoMovimentacao tipo,
            BigDecimal valor, LocalDate data, String descricao, UUID lancamentoId) {
        Movimentacao movimentacao = base(ambienteFinanceiroId, contaBancariaId, tipo, valor, data, descricao);
        movimentacao.origem = OrigemMovimentacao.LIQUIDACAO;
        movimentacao.lancamentoId = lancamentoId;
        return movimentacao;
    }

    /**
     * ADR-012: cria uma das duas pernas de uma Transferencia - o chamador
     * (servico de Transferencia) e responsavel por criar as duas
     * Movimentacoes (DEBITO na origem, CREDITO no destino) com o mesmo
     * {@code grupoTransferenciaId} na mesma transacao.
     */
    public static Movimentacao paraTransferencia(UUID ambienteFinanceiroId, UUID contaBancariaId,
            TipoMovimentacao tipo, BigDecimal valor, LocalDate data, String descricao, UUID grupoTransferenciaId) {
        Movimentacao movimentacao = base(ambienteFinanceiroId, contaBancariaId, tipo, valor, data, descricao);
        movimentacao.origem = OrigemMovimentacao.TRANSFERENCIA;
        movimentacao.grupoTransferenciaId = grupoTransferenciaId;
        return movimentacao;
    }

    /**
     * ADR-014: cria uma Movimentacao avulsa - registrada diretamente pelo
     * usuario (tarifa, juros, ajuste de saldo), sem Lancamento nem
     * Transferencia por tras. {@code categoriaId} e opcional; quando
     * preenchida, entra no relatorio por categoria (RelatorioService).
     * E a unica origem que pode ser editada/excluida livremente depois de
     * criada (ver {@link #editar}).
     */
    public static Movimentacao paraAvulsa(UUID ambienteFinanceiroId, UUID contaBancariaId, TipoMovimentacao tipo,
            BigDecimal valor, LocalDate data, String descricao, UUID categoriaId) {
        Movimentacao movimentacao = base(ambienteFinanceiroId, contaBancariaId, tipo, valor, data, descricao);
        movimentacao.origem = OrigemMovimentacao.MANUAL;
        movimentacao.categoriaId = categoriaId;
        return movimentacao;
    }

    /**
     * ADR-014: so permitido quando {@code origem=MANUAL} - Movimentacao de
     * LIQUIDACAO/TRANSFERENCIA continua imutavel (corrigir significa desfazer
     * a origem, ver {@code MovimentacaoService.excluir}).
     */
    public void editar(TipoMovimentacao tipo, BigDecimal valor, LocalDate data, String descricao, UUID categoriaId) {
        if (origem != OrigemMovimentacao.MANUAL) {
            throw new RegraDeNegocioException("MOVIMENTACAO_NAO_EDITAVEL",
                    "So e possivel editar uma Movimentacao avulsa (registrada manualmente).");
        }
        validarValor(valor);
        validarDescricao(descricao);
        this.tipo = tipo;
        this.valor = valor;
        this.data = data;
        this.descricao = descricao;
        this.categoriaId = categoriaId;
    }

    private static Movimentacao base(UUID ambienteFinanceiroId, UUID contaBancariaId, TipoMovimentacao tipo,
            BigDecimal valor, LocalDate data, String descricao) {
        validarValor(valor);
        validarDescricao(descricao);

        Movimentacao movimentacao = new Movimentacao();
        movimentacao.ambienteFinanceiroId = ambienteFinanceiroId;
        movimentacao.contaBancariaId = contaBancariaId;
        movimentacao.tipo = tipo;
        movimentacao.valor = valor;
        movimentacao.data = data;
        movimentacao.descricao = descricao;
        movimentacao.criadoEm = Instant.now();
        return movimentacao;
    }

    private static void validarValor(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException("VALOR_INVALIDO", "O valor da movimentacao deve ser maior que zero.");
        }
    }

    private static void validarDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            throw new RegraDeNegocioException("DESCRICAO_OBRIGATORIA", "Informe a descricao da movimentacao.");
        }
    }

    /** RN-12: sinal do valor para soma no saldo - CREDITO soma, DEBITO subtrai. */
    public BigDecimal valorComSinal() {
        return tipo == TipoMovimentacao.CREDITO ? valor : valor.negate();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAmbienteFinanceiroId() {
        return ambienteFinanceiroId;
    }

    public UUID getContaBancariaId() {
        return contaBancariaId;
    }

    public TipoMovimentacao getTipo() {
        return tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDate getData() {
        return data;
    }

    public String getDescricao() {
        return descricao;
    }

    public OrigemMovimentacao getOrigem() {
        return origem;
    }

    public UUID getCategoriaId() {
        return categoriaId;
    }

    public UUID getLancamentoId() {
        return lancamentoId;
    }

    public UUID getGrupoTransferenciaId() {
        return grupoTransferenciaId;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
