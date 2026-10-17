package br.com.unifinancas.infrastructure.ocr;

import br.com.unifinancas.infrastructure.ocr.OcrProperties.ProvedorIa;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Escolhe e instancia o cliente de IA de visão da etapa 4 da cascata
 * (ADR-016) conforme {@code unifinancas.ocr.ia.provedor} — <b>Gemini por
 * padrão</b> ({@link GeminiVisionClient}), OpenAI como alternativa
 * ({@link OpenAiVisionClient}). Mesmo padrão de "bean nulo" de
 * {@code ResendConfig}/{@code FcmConfig}: sem a chave do provedor escolhido,
 * o método devolve {@code null}, o Spring registra um null bean, e
 * {@code Optional<ClienteVisaoDocumento>} em {@link EtapaVisaoIa} resolve
 * para vazio — a cascata termina na etapa 3 e o processamento vai para
 * {@code ERRO}.
 */
@Configuration
@EnableConfigurationProperties(OcrProperties.class)
public class VisaoIaConfig {

    private static final Logger log = LoggerFactory.getLogger(VisaoIaConfig.class);

    @Bean
    public ClienteVisaoDocumento clienteVisaoDocumento(OcrProperties propriedades, RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        ProvedorIa provedor = propriedades.getIa().getProvedor();
        return provedor == ProvedorIa.OPENAI
                ? criarOpenAi(propriedades.getOpenai(), restClientBuilder, objectMapper)
                : criarGemini(propriedades.getGemini(), restClientBuilder, objectMapper);
    }

    private ClienteVisaoDocumento criarGemini(OcrProperties.Gemini gemini, RestClient.Builder builder,
            ObjectMapper objectMapper) {
        if (!StringUtils.hasText(gemini.getApiKey())) {
            log.info("Etapa de IA (Gemini) desabilitada: unifinancas.ocr.gemini.api-key ausente.");
            return null;
        }
        RestClient restClient = builder.clone()
                .requestFactory(fabrica(gemini.getTimeout()))
                .baseUrl(gemini.getBaseUrl())
                .defaultHeader("x-goog-api-key", gemini.getApiKey())
                .build();
        return new GeminiVisionClient(restClient, gemini.getModelo(), objectMapper);
    }

    private ClienteVisaoDocumento criarOpenAi(OcrProperties.Openai openai, RestClient.Builder builder,
            ObjectMapper objectMapper) {
        if (!StringUtils.hasText(openai.getApiKey())) {
            log.info("Etapa de IA (OpenAI) desabilitada: unifinancas.ocr.openai.api-key ausente.");
            return null;
        }
        RestClient restClient = builder.clone()
                .requestFactory(fabrica(openai.getTimeout()))
                .baseUrl(openai.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + openai.getApiKey())
                .build();
        return new OpenAiVisionClient(restClient, openai.getModelo(), objectMapper);
    }

    private static org.springframework.http.client.ClientHttpRequestFactory fabrica(Duration readTimeout) {
        return ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(readTimeout));
    }
}
