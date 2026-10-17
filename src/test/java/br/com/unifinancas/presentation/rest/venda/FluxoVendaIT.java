package br.com.unifinancas.presentation.rest.venda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.presentation.dto.CadastroResponse;
import br.com.unifinancas.presentation.dto.PaginaLancamentosResponse;
import br.com.unifinancas.presentation.dto.VendaResponse;
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
 * RF-19, T2.3.2/T2.3.3: cria uma Venda (sem cartao/parcelas, ao contrario de
 * Compra) e verifica que gera exatamente um Lancamento PENDENTE de origem
 * VENDA, tipo RECEITA - atraves dos controllers reais (MockMvc) contra um
 * Postgres real (Testcontainers). Cobertura de regra de negocio ja a fundo
 * em CompraVendaServiceTest - aqui so a integracao ponta a ponta via HTTP.
 */
@AutoConfigureMockMvc
class FluxoVendaIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void vendaGeraUmUnicoLancamentoReceitaPendente() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Marta Venda\",\"email\":\"marta.venda@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();

        String corpoVenda = "{\"data\":\"" + LocalDate.now() + "\",\"formaPagamento\":\"PIX\",\"itens\":["
                + "{\"produtoNome\":\"Bolo de fuba\",\"quantidade\":2,\"valorUnitario\":15.00}]}";
        VendaResponse venda = postJson("/v1/vendas", corpoVenda, token, ambienteId, VendaResponse.class, 201);
        assertThat(venda.valorTotal()).isEqualByComparingTo("30.00");
        assertThat(venda.itens()).hasSize(1);

        MvcResult resultadoLancamentos = mockMvc.perform(get("/v1/lancamentos")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andReturn();
        PaginaLancamentosResponse pagina = objectMapper.readValue(
                resultadoLancamentos.getResponse().getContentAsString(), PaginaLancamentosResponse.class);
        assertThat(pagina.itens()).hasSize(1).allSatisfy(lancamento -> {
            assertThat(lancamento.origem().name()).isEqualTo("VENDA");
            assertThat(lancamento.tipo().name()).isEqualTo("RECEITA");
            assertThat(lancamento.valor()).isEqualByComparingTo("30.00");
            assertThat(lancamento.status().name()).isEqualTo("PENDENTE");
        });
    }

    @Test
    void criarVendaSemItensRetornaValidacaoFalhou() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Paulo Venda\",\"email\":\"paulo.venda@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();

        String corpoVenda = "{\"data\":\"" + LocalDate.now() + "\",\"itens\":[]}";
        mockMvc.perform(post("/v1/vendas")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoVenda))
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
