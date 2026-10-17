package br.com.unifinancas.presentation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** SAD §7.2 - GET /v1/dashboard (RF-15). */
public record DashboardResponse(
        BigDecimal saldoTotal,
        BigDecimal receitasDoPeriodo,
        BigDecimal despesasDoPeriodo,
        BigDecimal resultadoDoPeriodo,
        ResumoVencimentosResponse vencimentos,
        List<OrcamentoMetaResponse> orcamentos,
        List<GastoPorCategoriaResponse> despesasPorCategoria,
        LocalDate inicioPeriodo,
        LocalDate fimPeriodo) {
}
