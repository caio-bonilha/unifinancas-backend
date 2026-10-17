package br.com.unifinancas.application.relatorio;

import java.math.BigDecimal;
import java.time.YearMonth;

/** T2.4.1, RF-22, ADR-018: um mes da serie de evolucao - saldo total (fim do mes), receitas e despesas do mes. */
public record PontoEvolucaoMensal(YearMonth mes, BigDecimal saldo, BigDecimal receitas, BigDecimal despesas) {
}
