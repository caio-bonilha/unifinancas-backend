package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** SAD §7.2, §4.9 - item de {@link CriarVendaRequest}. */
public record ItemVendaRequest(
        @NotBlank(message = "produtoNome e obrigatorio") String produtoNome,
        @NotNull(message = "quantidade e obrigatoria") BigDecimal quantidade,
        @NotNull(message = "valorUnitario e obrigatorio") BigDecimal valorUnitario) {
}
