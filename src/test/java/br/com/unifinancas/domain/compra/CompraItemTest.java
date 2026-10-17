package br.com.unifinancas.domain.compra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CompraItemTest {

    @Test
    void novoItemCalculaValorTotalComoQuantidadeVezesValorUnitario() {
        CompraItem item = CompraItem.novo("Detergente", new BigDecimal("3"), new BigDecimal("2.50"));

        assertThat(item.getProdutoNome()).isEqualTo("Detergente");
        assertThat(item.getQuantidade()).isEqualByComparingTo("3");
        assertThat(item.getValorUnitario()).isEqualByComparingTo("2.50");
        assertThat(item.getValorTotal()).isEqualByComparingTo("7.50");
        assertThat(item.getCriadoEm()).isNotNull();
    }

    @Test
    void valorTotalArredondaHalfUpEscalaDois() {
        CompraItem item = CompraItem.novo("Item fracionado", new BigDecimal("3"), new BigDecimal("0.335"));

        assertThat(item.getValorTotal()).isEqualByComparingTo("1.01");
    }

    @Test
    void produtoNomeEmBrancoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> CompraItem.novo(" ", BigDecimal.ONE, BigDecimal.TEN))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("PRODUTO_NOME_OBRIGATORIO"));
    }

    @Test
    void quantidadeZeroOuNegativaLancaRegraDeNegocio() {
        assertThatThrownBy(() -> CompraItem.novo("Item", BigDecimal.ZERO, BigDecimal.TEN))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("QUANTIDADE_INVALIDA"));

        assertThatThrownBy(() -> CompraItem.novo("Item", new BigDecimal("-1"), BigDecimal.TEN))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("QUANTIDADE_INVALIDA"));
    }

    @Test
    void valorUnitarioZeroOuNegativoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> CompraItem.novo("Item", BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("VALOR_UNITARIO_INVALIDO"));
    }
}
