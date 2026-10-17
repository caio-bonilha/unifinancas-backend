package br.com.unifinancas.domain.compra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompraTest {

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID contatoId = UUID.randomUUID();
    private final UUID cartaoId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();

    private List<CompraItem> doisItens() {
        return List.of(
                CompraItem.novo("Arroz", new BigDecimal("2"), new BigDecimal("25.00")),
                CompraItem.novo("Feijao", new BigDecimal("1"), new BigDecimal("8.50")));
    }

    @Test
    void novaCompraCalculaValorTotalComoSomaDosItens() {
        Compra compra = Compra.novo(ambienteFinanceiroId, contatoId, LocalDate.of(2026, 9, 1), FormaPagamento.PIX, null,
                null, documentoId, "Compras do mes", doisItens());

        assertThat(compra.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(compra.getContatoId()).isEqualTo(contatoId);
        assertThat(compra.getData()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(compra.getValorTotal()).isEqualByComparingTo("58.50");
        assertThat(compra.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(compra.getCartaoId()).isNull();
        assertThat(compra.getParcelas()).isEqualTo(1);
        assertThat(compra.getDocumentoId()).isEqualTo(documentoId);
        assertThat(compra.getItens()).hasSize(2);
        assertThat(compra.getDescricao()).isEqualTo("Compras do mes");
        assertThat(compra.getCriadoEm()).isNotNull();
    }

    @Test
    void parcelasNaoInformadasUsaUm() {
        Compra compra = Compra.novo(ambienteFinanceiroId, null, LocalDate.now(), null, null, null, null, null, doisItens());

        assertThat(compra.getParcelas()).isEqualTo(1);
    }

    @Test
    void descricaoEmBrancoViraNulo() {
        Compra compra = Compra.novo(ambienteFinanceiroId, null, LocalDate.now(), null, null, null, null, "   ", doisItens());

        assertThat(compra.getDescricao()).isNull();
    }

    @Test
    void compraNoCartaoExigeCartaoId() {
        Compra compra = Compra.novo(ambienteFinanceiroId, null, LocalDate.now(), FormaPagamento.CARTAO_CREDITO, cartaoId,
                3, null, null, doisItens());

        assertThat(compra.getCartaoId()).isEqualTo(cartaoId);
        assertThat(compra.getParcelas()).isEqualTo(3);
    }

    @Test
    void semItensLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Compra.novo(ambienteFinanceiroId, null, LocalDate.now(), null, null, null, null, null, List.of()))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("ITENS_OBRIGATORIOS"));

        assertThatThrownBy(() -> Compra.novo(ambienteFinanceiroId, null, LocalDate.now(), null, null, null, null, null, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("ITENS_OBRIGATORIOS"));
    }

    @Test
    void dataObrigatoriaLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Compra.novo(ambienteFinanceiroId, null, null, null, null, null, null, null, doisItens()))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DATA_OBRIGATORIA"));
    }

    @Test
    void parcelasMenorQueUmLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Compra.novo(ambienteFinanceiroId, null, LocalDate.now(), null, null, 0, null, null, doisItens()))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("PARCELAS_INVALIDAS"));
    }

    @Test
    void cartaoCreditoSemCartaoIdLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Compra.novo(
                        ambienteFinanceiroId, null, LocalDate.now(), FormaPagamento.CARTAO_CREDITO, null, null, null, null, doisItens()))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("CARTAO_OBRIGATORIO"));
    }

    @Test
    void cartaoIdSemFormaPagamentoCartaoCreditoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Compra.novo(ambienteFinanceiroId, null, LocalDate.now(), FormaPagamento.PIX, cartaoId, null,
                        null, null, doisItens()))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("CARTAO_INCOMPATIVEL_COM_FORMA_PAGAMENTO"));
    }
}
