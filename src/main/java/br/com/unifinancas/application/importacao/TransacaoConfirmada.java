package br.com.unifinancas.application.importacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * T4.1.4, RF-21: uma transacao que o usuario revisou e decidiu importar de
 * fato - {@code valor} ainda assinado (negativo = DESPESA, positivo =
 * RECEITA, mesmo criterio de {@link TransacaoImportada}); {@code categoriaId}
 * e o que o usuario escolheu (a sugestao de {@code ImportacaoService.analisar}
 * ou uma diferente); {@code identificadorExterno} e o resolvido devolvido por
 * {@code analisar} (ecoado de volta, nao recalculado na confirmacao).
 */
public record TransacaoConfirmada(LocalDate data, BigDecimal valor, String descricao, UUID categoriaId, String identificadorExterno) {
}
