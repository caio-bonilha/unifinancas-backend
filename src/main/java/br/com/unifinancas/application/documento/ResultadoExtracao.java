package br.com.unifinancas.application.documento;

/**
 * Resultado da cascata de extração (ADR-016) para um documento.
 *
 * <ul>
 *   <li><b>sucesso:</b> alguma etapa extraiu ao menos um {@code valor}
 *       monetário confiável. {@code dadosExtraidosJson} é o JSON no formato
 *       de {@code processamento_documento.dados_extraidos} (inclui
 *       {@code origemExtracao} e {@code camposFaltando}); o
 *       {@code ProcessamentoDocumentoOrchestrator} (T3.1.4) o grava e move o
 *       processamento para {@code AGUARDANDO_REVISAO}.</li>
 *   <li><b>falha:</b> nenhuma etapa aplicável produziu um valor (inclui o
 *       caso de a etapa OpenAI estar desabilitada ou indisponível).
 *       {@code motivo} explica; o orquestrador move para {@code ERRO} e
 *       dispara {@code FALHA_PROCESSAMENTO} (RN-08) — nunca cria Lançamento.</li>
 * </ul>
 */
public record ResultadoExtracao(boolean sucesso, String dadosExtraidosJson, OrigemExtracao origem, String motivo) {

    public static ResultadoExtracao sucesso(String dadosExtraidosJson, OrigemExtracao origem) {
        return new ResultadoExtracao(true, dadosExtraidosJson, origem, null);
    }

    public static ResultadoExtracao falha(String motivo) {
        return new ResultadoExtracao(false, null, null, motivo);
    }
}
