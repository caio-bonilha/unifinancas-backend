package br.com.unifinancas.domain.contabancaria;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContaBancariaTest {

    private final UUID ambienteFinanceiroId = UUID.randomUUID();

    @Test
    void novaContaNasceAtivaComSaldoAtualIgualAoInicialEMoedaBrl() {
        ContaBancaria conta = ContaBancaria.novo(ambienteFinanceiroId, "Conta Corrente", TipoContaBancaria.CORRENTE,
                new BigDecimal("100.00"));

        assertThat(conta.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(conta.getNome()).isEqualTo("Conta Corrente");
        assertThat(conta.getTipo()).isEqualTo(TipoContaBancaria.CORRENTE);
        assertThat(conta.getSaldoInicial()).isEqualByComparingTo("100.00");
        assertThat(conta.getSaldoAtual()).isEqualByComparingTo("100.00");
        assertThat(conta.getMoeda()).isEqualTo("BRL");
        assertThat(conta.isAtiva()).isTrue();
        assertThat(conta.getCriadoEm()).isNotNull();
        assertThat(conta.getAtualizadoEm()).isNotNull();
    }

    @Test
    void novaContaComSaldoZeroFuncionaNormalmente() {
        ContaBancaria conta = ContaBancaria.novo(ambienteFinanceiroId, "Carteira", TipoContaBancaria.DINHEIRO, BigDecimal.ZERO);

        assertThat(conta.getSaldoInicial()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(conta.getSaldoAtual()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
