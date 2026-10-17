package br.com.unifinancas.presentation.rest.compra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.presentation.dto.CadastroResponse;
import br.com.unifinancas.presentation.dto.CartaoResponse;
import br.com.unifinancas.presentation.dto.CompraResponse;
import br.com.unifinancas.presentation.dto.FaturaResponse;
import br.com.unifinancas.presentation.dto.PaginaLancamentosResponse;
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
 * RF-18, T2.2.2/T2.2.3: cria uma Compra no cartao (verifica que a Fatura
 * ABERTA atual ganha o valor) e uma Compra sem cartao (verifica que gera um
 * Lancamento PENDENTE de origem COMPRA) - atraves dos controllers reais
 * (MockMvc) contra um Postgres real (Testcontainers). Fluxo feliz de
 * pagamento com juros rotativo (RN-11) ja coberto a fundo em
 * FaturaServiceTest/CompraVendaServiceTest - aqui so a integracao ponta a
 * ponta via HTTP.
 */
@AutoConfigureMockMvc
class FluxoCompraIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void compraNoCartaoSomaValorNaFaturaAbertaAtual() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Helio Compra\",\"email\":\"helio.compra@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();

        String corpoCartao = "{\"nome\":\"Nubank\",\"limite\":5000.00,\"diaFechamento\":28,\"diaVencimento\":5}";
        CartaoResponse cartao = postJson("/v1/cartoes", corpoCartao, token, ambienteId, CartaoResponse.class, 201);

        String corpoCompra = "{\"data\":\"" + LocalDate.now() + "\",\"formaPagamento\":\"CARTAO_CREDITO\","
                + "\"cartaoId\":\"" + cartao.id() + "\",\"itens\":["
                + "{\"produtoNome\":\"Fone de ouvido\",\"quantidade\":1,\"valorUnitario\":150.00}]}";
        CompraResponse compra = postJson("/v1/compras", corpoCompra, token, ambienteId, CompraResponse.class, 201);
        assertThat(compra.valorTotal()).isEqualByComparingTo("150.00");
        assertThat(compra.itens()).hasSize(1);

        MvcResult resultadoFaturas = mockMvc.perform(get("/v1/cartoes/" + cartao.id() + "/faturas")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andReturn();
        FaturaResponse[] faturas =
                objectMapper.readValue(resultadoFaturas.getResponse().getContentAsString(), FaturaResponse[].class);
        assertThat(faturas).hasSize(1);
        assertThat(faturas[0].valorTotal()).isEqualByComparingTo("150.00");
    }

    @Test
    void compraSemCartaoGeraLancamentoPendenteDeOrigemCompra() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Ines Compra\",\"email\":\"ines.compra@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();

        String corpoCompra = "{\"data\":\"" + LocalDate.now() + "\",\"formaPagamento\":\"PIX\",\"itens\":["
                + "{\"produtoNome\":\"Feira da semana\",\"quantidade\":1,\"valorUnitario\":85.00}]}";
        postJson("/v1/compras", corpoCompra, token, ambienteId, CompraResponse.class, 201);

        MvcResult resultadoLancamentos = mockMvc.perform(get("/v1/lancamentos")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andReturn();
        PaginaLancamentosResponse pagina = objectMapper.readValue(
                resultadoLancamentos.getResponse().getContentAsString(), PaginaLancamentosResponse.class);
        assertThat(pagina.itens()).anySatisfy(lancamento -> {
            assertThat(lancamento.origem().name()).isEqualTo("COMPRA");
            assertThat(lancamento.valor()).isEqualByComparingTo("85.00");
            assertThat(lancamento.status().name()).isEqualTo("PENDENTE");
        });
    }

    @Test
    void criarCompraSemItensRetornaValidacaoFalhou() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Joao Compra\",\"email\":\"joao.compra@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();

        String corpoCompra = "{\"data\":\"" + LocalDate.now() + "\",\"itens\":[]}";
        mockMvc.perform(post("/v1/compras")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCompra))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"));
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
