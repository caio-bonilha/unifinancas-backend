package br.com.unifinancas.application.relatorio;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * T2.4.2, RF-22, ADR-018: um mes da projecao de fluxo de caixa - soma das
 * ocorrencias futuras de Lancamentos Recorrentes ativos + Lancamentos
 * PENDENTE/ATRASADO ja existentes (nao originados de recorrencia) com
 * vencimento no mes.
 */
public record PontoFluxoCaixaProjetado(YearMonth mes, BigDecimal receitasProjetadas, BigDecimal despesasProjetadas,
        BigDecimal resultadoProjetado) {
}
