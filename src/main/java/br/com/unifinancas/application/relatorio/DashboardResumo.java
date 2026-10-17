package br.com.unifinancas.application.relatorio;

import br.com.unifinancas.application.orcamentometa.OrcamentoMetaResumo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** T1.7.1, RF-15: agregados do dashboard (saldo, receitas, despesas, resultado, vencimentos, orcamento do periodo, visao por categoria). */
public record DashboardResumo(
        BigDecimal saldoTotal,
        BigDecimal receitasDoPeriodo,
        BigDecimal despesasDoPeriodo,
        BigDecimal resultadoDoPeriodo,
        ResumoVencimentos vencimentos,
        List<OrcamentoMetaResumo> orcamentos,
        List<GastoPorCategoria> despesasPorCategoria,
        LocalDate inicioPeriodo,
        LocalDate fimPeriodo) {
}
