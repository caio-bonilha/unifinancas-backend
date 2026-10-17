package br.com.unifinancas.presentation.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** SAD §7.2, §4.9. */
public record VendaItemResponse(UUID id, String produtoNome, BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorTotal) {
}
