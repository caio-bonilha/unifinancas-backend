package br.com.unifinancas.domain.contato;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContatoTest {

    private final UUID ambienteFinanceiroId = UUID.randomUUID();

    @Test
    void novoContatoNasceAtivoComTodosOsCamposOpcionaisPreenchidos() {
        Contato contato = Contato.novo(ambienteFinanceiroId, "Fornecedor Silva", TipoContato.FORNECEDOR,
                "12345678900", "fornecedor@example.com", "11999999999");

        assertThat(contato.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(contato.getNome()).isEqualTo("Fornecedor Silva");
        assertThat(contato.getTipo()).isEqualTo(TipoContato.FORNECEDOR);
        assertThat(contato.getDocumento()).isEqualTo("12345678900");
        assertThat(contato.getEmail()).isEqualTo("fornecedor@example.com");
        assertThat(contato.getTelefone()).isEqualTo("11999999999");
        assertThat(contato.isAtivo()).isTrue();
    }

    @Test
    void novoContatoSemCamposOpcionaisFuncionaNormalmente() {
        Contato contato = Contato.novo(ambienteFinanceiroId, "Cliente Rapido", TipoContato.CLIENTE, null, null, null);

        assertThat(contato.getDocumento()).isNull();
        assertThat(contato.getEmail()).isNull();
        assertThat(contato.getTelefone()).isNull();
        assertThat(contato.isAtivo()).isTrue();
    }
}
