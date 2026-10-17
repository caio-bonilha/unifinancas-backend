package br.com.unifinancas.presentation.dto;

import java.math.BigDecimal;

public record ResumoVencimentosResponse(
        long quantidadeContasAPagar, BigDecimal valorContasAPagar,
        long quantidadeContasAReceber, BigDecimal valorContasAReceber) {
}
