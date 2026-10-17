package br.com.unifinancas.domain.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VendaItemTest {

    @Test
    void novoItemCalculaValorTotalComoQuantidadeVezesValorUnitario() {
        VendaItem item = VendaItem.novo("Bolo de fuba", new BigDecimal("2"), new BigDecimal("15.00"));

        assertThat(item.getProdutoNome()).isEqualTo("Bolo de fuba");
        assertThat(item.getQuantidade()).isEqualByComparingTo("2");
        assertThat(item.getValorUnitario()).isEqualByComparingTo("15.00");
        assertThat(item.getValorTotal()).isEqualByComparingTo("30.00");
        assertThat(item.getCriadoEm()).isNotNull();
    }

    @Test
    void valorTotalArredondaHalfUpEscalaDois() {
        VendaItem item = VendaItem.novo("Item fracionado", new BigDecimal("3"), new BigDecimal("0.335"));

        assertThat(item.getValorTotal()).isEqualByComparingTo("1.01");
    }

    @Test
    void produtoNomeEmBrancoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> VendaItem.novo(" ", BigDecimal.ONE, BigDecimal.TEN))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("PRODUTO_NOME_OBRIGATORIO"));
    }

    @Test
    void quantidadeZeroOuNegativaLancaRegraDeNegocio() {
        assertThatThrownBy(() -> VendaItem.novo("Item", BigDecimal.ZERO, BigDecimal.TEN))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("QUANTIDADE_INVALIDA"));

        assertThatThrownBy(() -> VendaItem.novo("Item", new BigDecimal("-1"), BigDecimal.TEN))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("QUANTIDADE_INVALIDA"));
    }

    @Test
    void valorUnitarioZeroOuNegativoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> VendaItem.novo("Item", BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("VALOR_UNITARIO_INVALIDO"));
    }
}
