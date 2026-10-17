package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.contabancaria.TipoContaBancaria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** SAD §7.2 - POST /v1/contas-bancarias. {@code saldoInicial} e opcional (ausente = 0). */
public record CriarContaBancariaRequest(
        @NotBlank(message = "nome e obrigatorio") String nome,
        @NotNull(message = "tipo e obrigatorio") TipoContaBancaria tipo,
        @PositiveOrZero(message = "saldoInicial nao pode ser negativo") BigDecimal saldoInicial) {
}
