package br.com.unifinancas.presentation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** SAD §7.2, RF-22. */
public record RelatorioReceitasDespesasResponse(
        LocalDate inicioPeriodo, LocalDate fimPeriodo, BigDecimal receitas, BigDecimal despesas, BigDecimal resultado) {
}
