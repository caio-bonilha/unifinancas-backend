package br.com.unifinancas.presentation.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/** SAD §7.2, RF-22, ADR-018. */
public record PontoEvolucaoMensalResponse(YearMonth mes, BigDecimal saldo, BigDecimal receitas, BigDecimal despesas) {
}
