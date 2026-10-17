package br.com.unifinancas.domain.cartao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartaoTest {

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID contaBancariaId = UUID.randomUUID();

    @Test
    void novoCartaoNasceAtivoComOsCamposInformados() {
        Cartao cartao = Cartao.novo(ambienteFinanceiroId, contaBancariaId, "Nubank", new BigDecimal("5000.00"),
                10, 17, new BigDecimal("12.00"));

        assertThat(cartao.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(cartao.getContaBancariaId()).isEqualTo(contaBancariaId);
        assertThat(cartao.getNome()).isEqualTo("Nubank");
        assertThat(cartao.getLimite()).isEqualByComparingTo("5000.00");
        assertThat(cartao.getDiaFechamento()).isEqualTo(10);
        assertThat(cartao.getDiaVencimento()).isEqualTo(17);
        assertThat(cartao.getTaxaJurosRotativoMensal()).isEqualByComparingTo("12.00");
        assertThat(cartao.isAtivo()).isTrue();
        assertThat(cartao.getCriadoEm()).isNotNull();
    }

    @Test
    void novoCartaoSemTaxaDeJurosUsaZero() {
        Cartao cartao = Cartao.novo(ambienteFinanceiroId, null, "Cartao", new BigDecimal("1000.00"), 1, 10, null);

        assertThat(cartao.getTaxaJurosRotativoMensal()).isEqualByComparingTo("0");
    }

    @Test
    void limiteZeroOuNegativoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Cartao.novo(ambienteFinanceiroId, null, "Cartao", BigDecimal.ZERO, 1, 10, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("LIMITE_INVALIDO"));
    }

    @Test
    void diaFechamentoForaDoIntervaloLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Cartao.novo(ambienteFinanceiroId, null, "Cartao", BigDecimal.TEN, 0, 10, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DIA_INVALIDO"));

        assertThatThrownBy(() -> Cartao.novo(ambienteFinanceiroId, null, "Cartao", BigDecimal.TEN, 32, 10, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DIA_INVALIDO"));
    }

    @Test
    void diaVencimentoForaDoIntervaloLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Cartao.novo(ambienteFinanceiroId, null, "Cartao", BigDecimal.TEN, 10, 0, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DIA_INVALIDO"));
    }

    @Test
    void taxaDeJurosNegativaLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Cartao.novo(ambienteFinanceiroId, null, "Cartao", BigDecimal.TEN, 10, 17, new BigDecimal("-1")))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TAXA_JUROS_ROTATIVO_INVALIDA"));
    }

    @Test
    void editarAtualizaOsCampos() {
        Cartao cartao = Cartao.novo(ambienteFinanceiroId, null, "Nubank", new BigDecimal("5000.00"), 10, 17, null);
        UUID novaContaBancariaId = UUID.randomUUID();

        cartao.editar(novaContaBancariaId, "Nubank Ultravioleta", new BigDecimal("8000.00"), 5, 12, new BigDecimal("15.00"));

        assertThat(cartao.getContaBancariaId()).isEqualTo(novaContaBancariaId);
        assertThat(cartao.getNome()).isEqualTo("Nubank Ultravioleta");
        assertThat(cartao.getLimite()).isEqualByComparingTo("8000.00");
        assertThat(cartao.getDiaFechamento()).isEqualTo(5);
        assertThat(cartao.getDiaVencimento()).isEqualTo(12);
        assertThat(cartao.getTaxaJurosRotativoMensal()).isEqualByComparingTo("15.00");
    }

    @Test
    void ativarEDesativarAlternamOStatus() {
        Cartao cartao = Cartao.novo(ambienteFinanceiroId, null, "Cartao", BigDecimal.TEN, 10, 17, null);

        cartao.desativar();
        assertThat(cartao.isAtivo()).isFalse();

        cartao.ativar();
        assertThat(cartao.isAtivo()).isTrue();
    }
}
