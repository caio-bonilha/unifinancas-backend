package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.contabancaria.TipoContaBancaria;
import java.math.BigDecimal;
import java.util.UUID;

/** SAD §7.2, §4.4. */
public record ContaBancariaResponse(
        UUID id, String nome, TipoContaBancaria tipo, BigDecimal saldoInicial, BigDecimal saldoAtual, String moeda,
        boolean ativa) {
}
