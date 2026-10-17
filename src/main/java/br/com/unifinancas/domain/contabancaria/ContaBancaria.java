package br.com.unifinancas.domain.contabancaria;

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
import java.util.UUID;

/**
 * Origem/destino de recursos dentro de um Ambiente Financeiro (RF-05, SAD
 * Sec4.4). {@code saldoAtual} e derivado - recalculado a partir de
 * {@code saldoInicial} + soma dos lancamentos confirmados (Sec4.4); nasce
 * igual a {@code saldoInicial} porque ainda nao ha Lancamento apontando
 * para ela, e so sera atualizado quando a Etapa 1.3 (Lancamento) existir.
 * {@code moeda} e fixo BRL no MVP (Sec4.4, `[Proposto]`).
 */
@Entity
@Table(name = "conta_bancaria")
public class ContaBancaria {

    private static final String MOEDA_MVP = "BRL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    @Column(nullable = false, length = 100)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoContaBancaria tipo;

    @Column(name = "saldo_inicial", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoInicial;

    @Column(name = "saldo_atual", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoAtual;

    @Column(nullable = false, length = 3)
    private String moeda;

    @Column(nullable = false)
    private boolean ativa;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected ContaBancaria() {
        // exigido pelo JPA
    }

    public static ContaBancaria novo(UUID ambienteFinanceiroId, String nome, TipoContaBancaria tipo,
            BigDecimal saldoInicial) {
        ContaBancaria conta = new ContaBancaria();
        conta.ambienteFinanceiroId = ambienteFinanceiroId;
        conta.nome = nome;
        conta.tipo = tipo;
        conta.saldoInicial = saldoInicial;
        conta.saldoAtual = saldoInicial;
        conta.moeda = MOEDA_MVP;
        conta.ativa = true;
        Instant agora = Instant.now();
        conta.criadoEm = agora;
        conta.atualizadoEm = agora;
        return conta;
    }

    /**
     * SAD Sec4.4/Sec13.5 (T1.3.6): saldoAtual e sempre recomputado do zero a
     * partir de saldoInicial + soma dos lancamentos confirmados - nunca
     * incrementado/decrementado por delta, para nao acumular drift. Quem
     * calcula a soma e {@code ContaBancariaService.recalcularSaldo}; este
     * metodo so aplica o valor ja calculado.
     */
    public void atualizarSaldoAtual(BigDecimal novoSaldoAtual) {
        this.saldoAtual = novoSaldoAtual;
        this.atualizadoEm = Instant.now();
    }

    /**
     * T1.9.6 (gap identificado ao planejar a tela - T1.2.1 so tinha criar e
     * listar): so os campos descritivos - {@code saldoInicial} nao e
     * editavel aqui porque {@code saldoAtual} e derivado dele (Sec4.4) e
     * exigiria um recalculo (T1.3.6); fora de escopo desta tarefa.
     */
    public void editar(String nome, TipoContaBancaria tipo) {
        this.nome = nome;
        this.tipo = tipo;
        this.atualizadoEm = Instant.now();
    }

    public void ativar() {
        this.ativa = true;
        this.atualizadoEm = Instant.now();
    }

    public void desativar() {
        this.ativa = false;
        this.atualizadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAmbienteFinanceiroId() {
        return ambienteFinanceiroId;
    }

    public String getNome() {
        return nome;
    }

    public TipoContaBancaria getTipo() {
        return tipo;
    }

    public BigDecimal getSaldoInicial() {
        return saldoInicial;
    }

    public BigDecimal getSaldoAtual() {
        return saldoAtual;
    }

    public String getMoeda() {
        return moeda;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
