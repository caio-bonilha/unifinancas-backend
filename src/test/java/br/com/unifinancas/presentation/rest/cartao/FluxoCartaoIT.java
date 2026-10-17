package br.com.unifinancas.presentation.rest.cartao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.presentation.dto.CadastroResponse;
import br.com.unifinancas.presentation.dto.CartaoResponse;
import br.com.unifinancas.presentation.dto.FaturaResponse;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * RF-17, RN-11, ADR-017, T2.1.5: cria um Cartao (verifica que a primeira
 * Fatura ja nasce ABERTA) e confirma a validacao de transicao ao tentar
 * pagar uma Fatura ainda ABERTA - atraves dos controllers reais (MockMvc)
 * contra um Postgres real (Testcontainers). O fluxo feliz de pagamento
 * (RN-11, juros rotativo) so pode ser exercitado de ponta a ponta quando
 * Compra existir (Etapa 2.2, unico jeito de uma Fatura ganhar valor por
 * ora) - ja coberto a fundo em FaturaServiceTest.
 */
@AutoConfigureMockMvc
class FluxoCartaoIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void criaUmCartaoComAPrimeiraFaturaAbertaERecusaPagamentoAntesDoFechamento() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Fabio Cartao\",\"email\":\"fabio.cartao@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();

        String corpoCartao = "{\"nome\":\"Nubank\",\"limite\":5000.00,\"diaFechamento\":10,\"diaVencimento\":17,"
                + "\"taxaJurosRotativoMensal\":10.00}";
        CartaoResponse cartao = postJson("/v1/cartoes", corpoCartao, token, ambienteId, CartaoResponse.class, 201);
        assertThat(cartao.taxaJurosRotativoMensal()).isEqualByComparingTo("10.00");

        MvcResult resultadoFaturas = mockMvc.perform(get("/v1/cartoes/" + cartao.id() + "/faturas")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andReturn();
        FaturaResponse[] faturas = objectMapper.readValue(resultadoFaturas.getResponse().getContentAsString(), FaturaResponse[].class);
        assertThat(faturas).hasSize(1);
        assertThat(faturas[0].status().name()).isEqualTo("ABERTA");
        assertThat(faturas[0].valorTotal()).isEqualByComparingTo("0");

        // Fatura nasce ABERTA com valorTotal zero (Compras so existem na Etapa 2.2, ainda sem endpoint algum
        // para lancar valor nela) - so da pra exercitar aqui a validacao de transicao, nao um pagamento de
        // verdade (fechamento e so via job diario, T2.1.2, e nao ha nada para pagar antes de Compra existir).
        String corpoPagar = "{\"acao\":\"PAGAR\",\"valor\":10.00,\"dataPagamento\":\"" + LocalDate.now() + "\"}";
        mockMvc.perform(patch("/v1/cartoes/" + cartao.id() + "/faturas/" + faturas[0].id())
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPagar))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSICAO_INVALIDA"));
    }

    private <T> T postJson(String uri, String corpo, String tokenBearer, String ambienteFinanceiroId,
            Class<T> tipoResposta, int statusEsperado) throws Exception {
        var requestBuilder = post(uri).contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (tokenBearer != null) {
            requestBuilder = requestBuilder.header("Authorization", tokenBearer);
        }
        if (ambienteFinanceiroId != null) {
            requestBuilder = requestBuilder.header("X-Ambiente-Financeiro-Id", ambienteFinanceiroId);
        }
        MvcResult resultado = mockMvc.perform(requestBuilder).andExpect(status().is(statusEsperado)).andReturn();
        return objectMapper.readValue(resultado.getResponse().getContentAsString(), tipoResposta);
    }
}
