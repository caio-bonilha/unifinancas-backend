package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/** SAD §7.2 - POST /v1/cartoes. {@code contaBancariaId}/{@code taxaJurosRotativoMensal} sao opcionais (Sec4.5, ADR-017). */
public record CriarCartaoRequest(
        UUID contaBancariaId,
        @NotBlank(message = "nome e obrigatorio") String nome,
        @NotNull(message = "limite e obrigatorio") BigDecimal limite,
        @NotNull(message = "diaFechamento e obrigatorio") @Min(1) @Max(31) Integer diaFechamento,
        @NotNull(message = "diaVencimento e obrigatorio") @Min(1) @Max(31) Integer diaVencimento,
        @PositiveOrZero(message = "taxaJurosRotativoMensal nao pode ser negativa") BigDecimal taxaJurosRotativoMensal) {
}
