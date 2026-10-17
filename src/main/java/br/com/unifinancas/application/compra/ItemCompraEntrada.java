package br.com.unifinancas.application.compra;

import java.math.BigDecimal;

/** T2.2.2: dados de um item para {@link CompraVendaService#criar} - desacoplado do DTO web (T2.2.3). */
public record ItemCompraEntrada(String produtoNome, BigDecimal quantidade, BigDecimal valorUnitario) {
}
