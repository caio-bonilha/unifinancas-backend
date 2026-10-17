package br.com.unifinancas.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.unifinancas.domain.documento.TipoDocumento;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Teste de contrato da única integração externa da cascata (ADR-016, SAD
 * Seção 15.6): forma da requisição a {@code /chat/completions} e parsing da
 * resposta, contra um stub — sem chamar a OpenAI de verdade.
 */
class OpenAiVisionClientContractTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private OpenAiVisionClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://api.openai.test/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiVisionClient(builder.build(), "gpt-4o-mini", new ObjectMapper());
    }

    @Test
    void montaRequisicaoDeVisaoEParseiaOsCamposDaResposta() {
        String respostaModelo = """
                {"choices":[{"message":{"content":
                "{\\"valor\\":\\"260.35\\",\\"vencimento\\":\\"2026-09-10\\",\\"emissao\\":null,\
                \\"descricao\\":\\"ENEL SP\\",\\"documentoFiscal\\":null,\\"linhaDigitavel\\":null}"}}]}""";
        server.expect(requestTo("https://api.openai.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.messages[1].content[1].type").value("image_url"))
                .andExpect(jsonPath("$.messages[1].content[1].image_url.url").exists())
                .andRespond(withSuccess(respostaModelo, MediaType.APPLICATION_JSON));

        Optional<ExtracaoEtapa> r = client.extrair(new byte[] {1, 2, 3}, TipoDocumento.BOLETO);

        server.verify();
        assertThat(r).isPresent();
        assertThat(r.get().valor()).isEqualByComparingTo("260.35");
        assertThat(r.get().vencimento()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(r.get().descricao()).isEqualTo("ENEL SP");
    }

    @Test
    void aceitaRespostaComCercaDeMarkdownAoRedorDoJson() {
        String respostaModelo = "{\"choices\":[{\"message\":{\"content\":"
                + "\"```json\\n{\\\"valor\\\":\\\"12.34\\\"}\\n```\"}}]}";
        server.expect(requestTo("https://api.openai.test/v1/chat/completions"))
                .andRespond(withSuccess(respostaModelo, MediaType.APPLICATION_JSON));

        Optional<ExtracaoEtapa> r = client.extrair(new byte[] {9}, TipoDocumento.CUPOM_FISCAL);

        assertThat(r).isPresent();
        assertThat(r.get().valor()).isEqualByComparingTo("12.34");
    }

    @Test
    void respostaSemValorParseavelResultaEmVazio() {
        server.expect(requestTo("https://api.openai.test/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"desculpe, nao consegui\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.extrair(new byte[] {1}, TipoDocumento.COMPROVANTE)).isEmpty();
    }

    @Test
    void erroHttpPropagaParaAResilienciaTratar() {
        server.expect(requestTo("https://api.openai.test/v1/chat/completions"))
                .andRespond(withServerError());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client.extrair(new byte[] {1}, TipoDocumento.BOLETO))
                .isInstanceOf(RuntimeException.class);
    }
}
