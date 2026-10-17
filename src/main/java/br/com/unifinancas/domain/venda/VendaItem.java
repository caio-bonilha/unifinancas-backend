package br.com.unifinancas.domain.venda;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Item de uma Venda (RF-19, SAD Sec4.9/Sec5.2) - simetrico a
 * {@code CompraItem}. Sem repositorio proprio - so existe dentro do
 * agregado {@code Venda} (Venda "1" --> "1..*" VendaItem no diagrama de
 * dominio), acessado sempre via {@code Venda.getItens()}.
 */
@Entity
@Table(name = "venda_item")
public class VendaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "produto_nome", nullable = false, length = 200)
    private String produtoNome;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected VendaItem() {
        // exigido pelo JPA
    }

    /** {@code valorTotal} e sempre derivado (quantidade * valorUnitario, arredondado HALF_UP escala 2) - mesmo criterio de {@code CompraItem.novo}. */
    public static VendaItem novo(String produtoNome, BigDecimal quantidade, BigDecimal valorUnitario) {
        VendaItem item = new VendaItem();
        item.produtoNome = validarProdutoNome(produtoNome);
        item.quantidade = validarQuantidade(quantidade);
        item.valorUnitario = validarValorUnitario(valorUnitario);
        item.valorTotal = item.quantidade.multiply(item.valorUnitario).setScale(2, RoundingMode.HALF_UP);
        Instant agora = Instant.now();
        item.criadoEm = agora;
        item.atualizadoEm = agora;
        return item;
    }

    private static String validarProdutoNome(String produtoNome) {
        if (produtoNome == null || produtoNome.isBlank()) {
            throw new RegraDeNegocioException("PRODUTO_NOME_OBRIGATORIO", "produtoNome e obrigatorio.");
        }
        return produtoNome;
    }

    private static BigDecimal validarQuantidade(BigDecimal quantidade) {
        if (quantidade == null || quantidade.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException("QUANTIDADE_INVALIDA", "A quantidade deve ser maior que zero.");
        }
        return quantidade;
    }

    private static BigDecimal validarValorUnitario(BigDecimal valorUnitario) {
        if (valorUnitario == null || valorUnitario.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException("VALOR_UNITARIO_INVALIDO", "O valor unitario deve ser maior que zero.");
        }
        return valorUnitario;
    }

    public UUID getId() {
        return id;
    }

    public String getProdutoNome() {
        return produtoNome;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
