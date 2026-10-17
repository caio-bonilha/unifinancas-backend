package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.orcamentometa.PeriodoOrcamentoMeta;
import br.com.unifinancas.domain.orcamentometa.StatusOrcamentoMeta;
import br.com.unifinancas.domain.orcamentometa.TipoOrcamentoMeta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * SAD §7.2, §4.11. {@code valorAtual}/{@code percentualAtingido} sao o
 * progresso calculado sob demanda (RN-10) - nulos para META sem
 * {@code contaBancariaId} vinculada.
 */
public record OrcamentoMetaResponse(
        UUID id, TipoOrcamentoMeta tipo, UUID categoriaId, String nome, BigDecimal valorLimite, BigDecimal valorAlvo,
        PeriodoOrcamentoMeta periodo, LocalDate dataInicio, LocalDate dataFim, UUID contaBancariaId,
        StatusOrcamentoMeta status, BigDecimal valorAtual, BigDecimal percentualAtingido, BigDecimal limiarAlertaPercentual) {
}
