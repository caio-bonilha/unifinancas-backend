package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.orcamentometa.TipoOrcamentoMeta;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * SAD §7.2 - POST /v1/orcamentos-metas. Campos usados dependem de
 * {@code tipo}: ORCAMENTO usa {@code categoriaId}/{@code valorLimite}/
 * {@code limiarAlertaPercentual}; META usa {@code nome}/{@code valorAlvo}/
 * {@code contaBancariaId}. A obrigatoriedade de cada campo por tipo e
 * validada na propria entidade (OrcamentoMeta.novoOrcamento/novaMeta,
 * T1.4.1/T1.5.3) - nao repetida aqui. {@code limiarAlertaPercentual} e
 * opcional (ausente = 80%, T1.5.3, Fluxo 6.4).
 */
public record CriarOrcamentoMetaRequest(
        @NotNull(message = "tipo e obrigatorio") TipoOrcamentoMeta tipo,
        UUID categoriaId,
        BigDecimal valorLimite,
        BigDecimal limiarAlertaPercentual,
        String nome,
        BigDecimal valorAlvo,
        UUID contaBancariaId,
        LocalDate dataInicio,
        LocalDate dataFim) {
}
