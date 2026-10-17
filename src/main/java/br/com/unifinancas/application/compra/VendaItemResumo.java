package br.com.unifinancas.application.compra;

import java.math.BigDecimal;
import java.util.UUID;

/** T2.3.2. */
public record VendaItemResumo(UUID id, String produtoNome, BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorTotal) {
}
