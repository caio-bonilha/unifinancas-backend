package br.com.unifinancas.application.relatorio;

import java.math.BigDecimal;
import java.time.LocalDate;

/** T2.4.1, RF-22: totais de receitas/despesas de um periodo arbitrario (nao fixo no mes corrente, ao contrario do Dashboard). */
public record RelatorioReceitasDespesas(
        LocalDate inicioPeriodo, LocalDate fimPeriodo, BigDecimal receitas, BigDecimal despesas, BigDecimal resultado) {
}
