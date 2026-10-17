package br.com.unifinancas.presentation.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** SAD §7.2, §4.8. */
public record CompraItemResponse(UUID id, String produtoNome, BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorTotal) {
}
