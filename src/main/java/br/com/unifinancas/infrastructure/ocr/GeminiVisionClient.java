package br.com.unifinancas.infrastructure.ocr;

import br.com.unifinancas.domain.documento.TipoDocumento;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.client.RestClient;

/**
 * Cliente do <b>Google Gemini</b> (API {@code generateContent}, visão) para a
 * etapa 4 da cascata (ADR-016) — provedor de IA padrão (decisão consultada
 * com o usuário, revisão de T3.1.3). A chave vai no header
 * {@code x-goog-api-key} (configurado no {@link RestClient} por
 * {@link VisaoIaConfig}), não na URL.
 *
 * <p>Resiliência (SAD Seção 7.6): {@link Retry} 3× com backoff exponencial e
 * {@link CircuitBreaker}, instância {@code ocr-ia}; timeout curto no
 * {@link RestClient}. Prompt e parsing são compartilhados com o cliente da
 * OpenAI via {@link RespostaIaParser}.
 */
public class GeminiVisionClient implements ClienteVisaoDocumento {

    private final RestClient restClient;
    private final String modelo;
    private final ObjectMapper objectMapper;

    public GeminiVisionClient(RestClient restClient, String modelo, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.modelo = modelo;
        this.objectMapper = objectMapper;
    }

    @Override
    @Retry(name = "ocr-ia")
    @CircuitBreaker(name = "ocr-ia")
    public Optional<ExtracaoEtapa> extrair(byte[] imagemPng, TipoDocumento tipo) {
        String base64 = Base64.getEncoder().encodeToString(imagemPng);
        Map<String, Object> corpo = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", RespostaIaParser.PROMPT_SISTEMA))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(
                        Map.of("text", RespostaIaParser.instrucaoDoTipo(tipo.name())),
                        Map.of("inlineData", Map.of("mimeType", "image/png", "data", base64))))),
                "generationConfig", Map.of("temperature", 0, "responseMimeType", "application/json"));

        JsonNode resposta = restClient.post()
                .uri("/models/{modelo}:generateContent", modelo)
                .body(corpo)
                .retrieve()
                .body(JsonNode.class);

        String conteudo = Optional.ofNullable(resposta)
                .map(r -> r.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null))
                .orElse(null);
        return RespostaIaParser.parse(conteudo, objectMapper);
    }
}
