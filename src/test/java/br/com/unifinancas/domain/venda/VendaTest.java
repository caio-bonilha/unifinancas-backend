package br.com.unifinancas.domain.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VendaTest {

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID contatoId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();

    private List<VendaItem> doisItens() {
        return List.of(
                VendaItem.novo("Bolo de fuba", new BigDecimal("2"), new BigDecimal("15.00")),
                VendaItem.novo("Suco natural", new BigDecimal("1"), new BigDecimal("8.00")));
    }

    @Test
    void novaVendaCalculaValorTotalComoSomaDosItens() {
        Venda venda = Venda.novo(ambienteFinanceiroId, contatoId, LocalDate.of(2026, 9, 1), FormaPagamento.PIX,
                documentoId, "Venda do dia", doisItens());

        assertThat(venda.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(venda.getContatoId()).isEqualTo(contatoId);
        assertThat(venda.getData()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(venda.getValorTotal()).isEqualByComparingTo("38.00");
        assertThat(venda.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(venda.getDocumentoId()).isEqualTo(documentoId);
        assertThat(venda.getItens()).hasSize(2);
        assertThat(venda.getDescricao()).isEqualTo("Venda do dia");
        assertThat(venda.getCriadoEm()).isNotNull();
    }

    @Test
    void descricaoEmBrancoViraNulo() {
        Venda venda = Venda.novo(ambienteFinanceiroId, null, LocalDate.now(), null, null, "   ", doisItens());

        assertThat(venda.getDescricao()).isNull();
    }

    @Test
    void semItensLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Venda.novo(ambienteFinanceiroId, null, LocalDate.now(), null, null, null, List.of()))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("ITENS_OBRIGATORIOS"));

        assertThatThrownBy(() -> Venda.novo(ambienteFinanceiroId, null, LocalDate.now(), null, null, null, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("ITENS_OBRIGATORIOS"));
    }

    @Test
    void dataObrigatoriaLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Venda.novo(ambienteFinanceiroId, null, null, null, null, null, doisItens()))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DATA_OBRIGATORIA"));
    }
}
