package br.com.unifinancas.application.importacao;

import java.util.UUID;

/**
 * T4.1.3, RF-21: resultado de {@link ImportacaoService#analisar} para uma
 * {@link TransacaoImportada} - {@code identificadorExternoResolvido} e o
 * que vai ser gravado em {@code Lancamento.identificadorExterno} se esta
 * transacao for confirmada (T4.1.4); {@code categoriaSugeridaId} so vem
 * preenchida quando a transacao NAO e duplicada (sugerir categoria pra algo
 * que nao vai ser importado nao faz sentido).
 */
public record TransacaoAnalisada(
        TransacaoImportada transacao, String identificadorExternoResolvido, boolean duplicada,
        UUID lancamentoDuplicadoId, UUID categoriaSugeridaId) {
}
