package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** SAD §7.2 - POST /v1/movimentacoes/transferencias (ADR-012, RN-05). */
public record CriarTransferenciaRequest(
        @NotNull(message = "contaBancariaOrigemId e obrigatorio") UUID contaBancariaOrigemId,
        @NotNull(message = "contaBancariaDestinoId e obrigatorio") UUID contaBancariaDestinoId,
        @NotNull(message = "valor e obrigatorio") BigDecimal valor,
        @NotNull(message = "data e obrigatoria") LocalDate data,
        @NotBlank(message = "descricao e obrigatoria") String descricao) {
}
