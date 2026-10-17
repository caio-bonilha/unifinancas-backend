package br.com.unifinancas.application.contabancaria;

import br.com.unifinancas.domain.contabancaria.TipoContaBancaria;
import java.math.BigDecimal;
import java.util.UUID;

public record ContaBancariaResumo(
        UUID id, String nome, TipoContaBancaria tipo, BigDecimal saldoInicial, BigDecimal saldoAtual, String moeda,
        boolean ativa) {
}
