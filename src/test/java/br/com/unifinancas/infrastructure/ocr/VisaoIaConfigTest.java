package br.com.unifinancas.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.unifinancas.infrastructure.ocr.OcrProperties.ProvedorIa;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class VisaoIaConfigTest {

    private final VisaoIaConfig config = new VisaoIaConfig();
    private final RestClient.Builder builder = RestClient.builder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private OcrProperties props(ProvedorIa provedor, String geminiKey, String openaiKey) {
        OcrProperties p = new OcrProperties();
        p.getIa().setProvedor(provedor);
        p.getGemini().setApiKey(geminiKey);
        p.getOpenai().setApiKey(openaiKey);
        return p;
    }

    @Test
    void padraoGeminiComChaveCriaClienteGemini() {
        ClienteVisaoDocumento cliente = config.clienteVisaoDocumento(
                props(ProvedorIa.GEMINI, "chave-gemini", ""), builder, objectMapper);

        assertThat(cliente).isInstanceOf(GeminiVisionClient.class);
    }

    @Test
    void geminiSemChaveResultaEmBeanNulo() {
        ClienteVisaoDocumento cliente = config.clienteVisaoDocumento(
                props(ProvedorIa.GEMINI, "", "chave-openai"), builder, objectMapper);

        assertThat(cliente).isNull();
    }

    @Test
    void provedorOpenaiComChaveCriaClienteOpenAi() {
        ClienteVisaoDocumento cliente = config.clienteVisaoDocumento(
                props(ProvedorIa.OPENAI, "chave-gemini", "chave-openai"), builder, objectMapper);

        assertThat(cliente).isInstanceOf(OpenAiVisionClient.class);
    }

    @Test
    void provedorOpenaiSemChaveResultaEmBeanNulo() {
        ClienteVisaoDocumento cliente = config.clienteVisaoDocumento(
                props(ProvedorIa.OPENAI, "chave-gemini", ""), builder, objectMapper);

        assertThat(cliente).isNull();
    }
}
