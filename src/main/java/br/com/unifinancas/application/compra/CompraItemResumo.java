package br.com.unifinancas.application.compra;

import java.math.BigDecimal;
import java.util.UUID;

/** T2.2.2. */
public record CompraItemResumo(UUID id, String produtoNome, BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorTotal) {
}
