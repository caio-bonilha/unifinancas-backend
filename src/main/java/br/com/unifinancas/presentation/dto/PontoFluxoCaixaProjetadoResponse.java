package br.com.unifinancas.presentation.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/** SAD §7.2, RF-22, ADR-018. */
public record PontoFluxoCaixaProjetadoResponse(
        YearMonth mes, BigDecimal receitasProjetadas, BigDecimal despesasProjetadas, BigDecimal resultadoProjetado) {
}
