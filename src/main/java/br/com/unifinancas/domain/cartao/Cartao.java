package br.com.unifinancas.domain.cartao;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Meio de pagamento com limite e ciclo de fatura (RF-17, SAD Sec4.5).
 * {@code taxaJurosRotativoMensal} (ADR-017, decisao consultada com o
 * usuario): percentual mensal configuravel por Cartao (nao fixo do sistema
 * nem por Ambiente Financeiro) - aplicado ao saldo remanescente de uma
 * Fatura paga parcialmente (RN-11, {@code FaturaService}).
 */
@Entity
@Table(name = "cartao")
public class Cartao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    /** Conta de pagamento da fatura - opcional (Sec4.5). */
    @Column(name = "conta_bancaria_id")
    private UUID contaBancariaId;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal limite;

    @Column(name = "dia_fechamento", nullable = false)
    private int diaFechamento;

    @Column(name = "dia_vencimento", nullable = false)
    private int diaVencimento;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "taxa_juros_rotativo_mensal", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxaJurosRotativoMensal;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Cartao() {
        // exigido pelo JPA
    }

    public static Cartao novo(UUID ambienteFinanceiroId, UUID contaBancariaId, String nome, BigDecimal limite,
            int diaFechamento, int diaVencimento, BigDecimal taxaJurosRotativoMensal) {
        Cartao cartao = new Cartao();
        cartao.ambienteFinanceiroId = ambienteFinanceiroId;
        cartao.contaBancariaId = contaBancariaId;
        cartao.nome = nome;
        cartao.limite = validarLimite(limite);
        cartao.diaFechamento = validarDia(diaFechamento, "diaFechamento");
        cartao.diaVencimento = validarDia(diaVencimento, "diaVencimento");
        cartao.taxaJurosRotativoMensal = validarTaxa(taxaJurosRotativoMensal);
        cartao.ativo = true;
        Instant agora = Instant.now();
        cartao.criadoEm = agora;
        cartao.atualizadoEm = agora;
        return cartao;
    }

    /** Campos descritivos - mesmo padrao de {@code ContaBancaria.editar}: so o que faz sentido corrigir depois de criado. */
    public void editar(UUID contaBancariaId, String nome, BigDecimal limite, int diaFechamento, int diaVencimento,
            BigDecimal taxaJurosRotativoMensal) {
        this.contaBancariaId = contaBancariaId;
        this.nome = nome;
        this.limite = validarLimite(limite);
        this.diaFechamento = validarDia(diaFechamento, "diaFechamento");
        this.diaVencimento = validarDia(diaVencimento, "diaVencimento");
        this.taxaJurosRotativoMensal = validarTaxa(taxaJurosRotativoMensal);
        this.atualizadoEm = Instant.now();
    }

    public void ativar() {
        this.ativo = true;
        this.atualizadoEm = Instant.now();
    }

    public void desativar() {
        this.ativo = false;
        this.atualizadoEm = Instant.now();
    }

    private static BigDecimal validarLimite(BigDecimal limite) {
        if (limite == null || limite.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException("LIMITE_INVALIDO", "O limite do Cartao deve ser maior que zero.");
        }
        return limite;
    }

    private static int validarDia(Integer dia, String nomeCampo) {
        if (dia == null || dia < 1 || dia > 31) {
            throw new RegraDeNegocioException("DIA_INVALIDO", nomeCampo + " deve estar entre 1 e 31.");
        }
        return dia;
    }

    private static BigDecimal validarTaxa(BigDecimal taxa) {
        BigDecimal valor = taxa != null ? taxa : BigDecimal.ZERO;
        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new RegraDeNegocioException(
                    "TAXA_JUROS_ROTATIVO_INVALIDA", "taxaJurosRotativoMensal nao pode ser negativa.");
        }
        return valor;
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

    public String getNome() {
        return nome;
    }

    public BigDecimal getLimite() {
        return limite;
    }

    public int getDiaFechamento() {
        return diaFechamento;
    }

    public int getDiaVencimento() {
        return diaVencimento;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public BigDecimal getTaxaJurosRotativoMensal() {
        return taxaJurosRotativoMensal;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
