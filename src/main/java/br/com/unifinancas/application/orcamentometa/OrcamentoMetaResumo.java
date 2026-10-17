package br.com.unifinancas.application.orcamentometa;

import br.com.unifinancas.domain.orcamentometa.PeriodoOrcamentoMeta;
import br.com.unifinancas.domain.orcamentometa.StatusOrcamentoMeta;
import br.com.unifinancas.domain.orcamentometa.TipoOrcamentoMeta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code valorAtual}/{@code percentualAtingido} sao o progresso calculado
 * sob demanda (RN-10) - nunca persistidos (SAD Sec4.11/Sec9). Para META sem
 * {@code contaBancariaId} vinculada nao ha como calcular progresso ainda
 * (nenhuma outra fonte de dado o sustenta) - ambos ficam nulos nesse caso.
 */
public record OrcamentoMetaResumo(
        UUID id, TipoOrcamentoMeta tipo, UUID categoriaId, String nome, BigDecimal valorLimite, BigDecimal valorAlvo,
        PeriodoOrcamentoMeta periodo, LocalDate dataInicio, LocalDate dataFim, UUID contaBancariaId,
        StatusOrcamentoMeta status, BigDecimal valorAtual, BigDecimal percentualAtingido, BigDecimal limiarAlertaPercentual) {
}
