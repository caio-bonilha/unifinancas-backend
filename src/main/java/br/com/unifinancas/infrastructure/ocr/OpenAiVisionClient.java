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
 * Cliente da <b>OpenAI API</b> (visão) para a etapa 4 da cascata (ADR-016).
 * Mantido como alternativa: por padrão o provedor é o Gemini
 * ({@link GeminiVisionClient}); a troca é feita só por
 * {@code unifinancas.ocr.ia.provedor} ({@link VisaoIaConfig}), sem tocar na
 * cascata.
 *
 * <p>Resiliência (SAD Seção 7.6): {@link Retry} 3× com backoff exponencial e
 * {@link CircuitBreaker}, instância {@code ocr-ia} no {@code application.yml};
 * timeout curto no próprio {@link RestClient}.
 */
public class OpenAiVisionClient implements ClienteVisaoDocumento {

    private final RestClient restClient;
    private final String modelo;
    private final ObjectMapper objectMapper;

    public OpenAiVisionClient(RestClient restClient, String modelo, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.modelo = modelo;
        this.objectMapper = objectMapper;
    }

    @Override
    @Retry(name = "ocr-ia")
    @CircuitBreaker(name = "ocr-ia")
    public Optional<ExtracaoEtapa> extrair(byte[] imagemPng, TipoDocumento tipo) {
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(imagemPng);
        Map<String, Object> corpo = Map.of(
                "model", modelo,
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", RespostaIaParser.PROMPT_SISTEMA),
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", RespostaIaParser.instrucaoDoTipo(tipo.name())),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUri))))));

        JsonNode resposta = restClient.post()
                .uri("/chat/completions")
                .body(corpo)
                .retrieve()
                .body(JsonNode.class);

        String conteudo = Optional.ofNullable(resposta)
                .map(r -> r.path("choices").path(0).path("message").path("content").asText(null))
                .orElse(null);
        return RespostaIaParser.parse(conteudo, objectMapper);
    }
}
