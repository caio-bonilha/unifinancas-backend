package br.com.unifinancas.application.compra;

import java.math.BigDecimal;

/** T2.3.2: dados de um item para {@link CompraVendaService#criarVenda} - desacoplado do DTO web (T2.3.3). */
public record ItemVendaEntrada(String produtoNome, BigDecimal quantidade, BigDecimal valorUnitario) {
}
