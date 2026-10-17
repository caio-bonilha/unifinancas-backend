package br.com.unifinancas.presentation.rest.lancamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.presentation.dto.CadastroResponse;
import br.com.unifinancas.presentation.dto.ContaBancariaResponse;
import br.com.unifinancas.presentation.dto.LancamentoResponse;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * ADR-015, T1.3c.5: Conta Bancaria e Forma de Pagamento exigidas na
 * quitacao (individual e em lote) - atraves dos controllers reais (MockMvc)
 * contra um Postgres real (Testcontainers).
 */
@AutoConfigureMockMvc
class FluxoQuitacaoIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void confirmarPagamentoSemContaBancariaNemFormaDePagamentoVira422() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Bia Quitacao\",\"email\":\"bia.quitacao@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();
        LancamentoResponse lancamento = criarDespesa(token, ambienteId, null, null);

        mockMvc.perform(patch("/v1/lancamentos/" + lancamento.id())
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"CONFIRMAR_PAGAMENTO\",\"dataPagamento\":\"2026-08-25\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CONTA_BANCARIA_OBRIGATORIA_PARA_QUITACAO"));
    }

    @Test
    void confirmarPagamentoComContaEFormaNaConfirmacaoQuitaEGravaOsCampos() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Carlos Quitacao\",\"email\":\"carlos.quitacao@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();
        ContaBancariaResponse conta = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Corrente\",\"tipo\":\"CORRENTE\",\"saldoInicial\":500.00}", token, ambienteId,
                ContaBancariaResponse.class, 201);
        LancamentoResponse lancamento = criarDespesa(token, ambienteId, null, null);

        String corpo = "{\"acao\":\"CONFIRMAR_PAGAMENTO\",\"dataPagamento\":\"2026-08-25\",\"contaBancariaId\":\""
                + conta.id() + "\",\"formaPagamento\":\"PIX\"}";
        mockMvc.perform(patch("/v1/lancamentos/" + lancamento.id())
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGO"))
                .andExpect(jsonPath("$.contaBancariaId").value(conta.id().toString()))
                .andExpect(jsonPath("$.formaPagamento").value("PIX"));
    }

    /** ADR-015: quitacao em lote confirma varios de uma vez, usando Conta+Forma so nos que ainda nao tinham. */
    @Test
    void quitacaoEmLoteConfirmaVariosMantendoOQueJaTinhaEUsandoOFallbackNosQueNaoTinham() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Duda Quitacao\",\"email\":\"duda.quitacao@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();
        ContaBancariaResponse contaOriginal = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Original\",\"tipo\":\"CORRENTE\",\"saldoInicial\":500.00}", token, ambienteId,
                ContaBancariaResponse.class, 201);
        ContaBancariaResponse contaDoLote = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Do lote\",\"tipo\":\"CORRENTE\",\"saldoInicial\":500.00}", token, ambienteId,
                ContaBancariaResponse.class, 201);
        LancamentoResponse comConta = criarDespesa(token, ambienteId, contaOriginal.id().toString(), "BOLETO");
        LancamentoResponse semConta = criarDespesa(token, ambienteId, null, null);

        String corpo = "{\"ids\":[\"" + comConta.id() + "\",\"" + semConta.id() + "\"],\"dataPagamento\":\"2026-08-25\","
                + "\"contaBancariaId\":\"" + contaDoLote.id() + "\",\"formaPagamento\":\"PIX\"}";
        MvcResult resultado = mockMvc.perform(patch("/v1/lancamentos/quitacao-em-lote")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andReturn();

        LancamentoResponse[] confirmados =
                objectMapper.readValue(resultado.getResponse().getContentAsString(), LancamentoResponse[].class);
        LancamentoResponse confirmadoComConta = java.util.Arrays.stream(confirmados)
                .filter(l -> l.id().equals(comConta.id())).findFirst().orElseThrow();
        LancamentoResponse confirmadoSemConta = java.util.Arrays.stream(confirmados)
                .filter(l -> l.id().equals(semConta.id())).findFirst().orElseThrow();

        assertThat(confirmadoComConta.status().name()).isEqualTo("PAGO");
        assertThat(confirmadoComConta.contaBancariaId()).isEqualTo(contaOriginal.id());
        assertThat(confirmadoComConta.formaPagamento().name()).isEqualTo("BOLETO");
        assertThat(confirmadoSemConta.status().name()).isEqualTo("PAGO");
        assertThat(confirmadoSemConta.contaBancariaId()).isEqualTo(contaDoLote.id());
        assertThat(confirmadoSemConta.formaPagamento().name()).isEqualTo("PIX");
    }

    @Test
    void quitacaoEmLoteComTiposMistosVira422EmTodoOStack() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Eva Quitacao\",\"email\":\"eva.quitacao@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();
        ContaBancariaResponse conta = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Corrente\",\"tipo\":\"CORRENTE\",\"saldoInicial\":500.00}", token, ambienteId,
                ContaBancariaResponse.class, 201);
        LancamentoResponse despesa = criarDespesa(token, ambienteId, null, null);
        String corpoReceita = "{\"tipo\":\"RECEITA\",\"valor\":100.00,\"dataEmissao\":\"2026-08-25\","
                + "\"dataVencimento\":\"2026-08-25\",\"descricao\":\"Salário\"}";
        LancamentoResponse receita = postJson("/v1/lancamentos", corpoReceita, token, ambienteId, LancamentoResponse.class, 201);

        String corpo = "{\"ids\":[\"" + despesa.id() + "\",\"" + receita.id() + "\"],\"dataPagamento\":\"2026-08-25\","
                + "\"contaBancariaId\":\"" + conta.id() + "\",\"formaPagamento\":\"PIX\"}";
        mockMvc.perform(patch("/v1/lancamentos/quitacao-em-lote")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("LOTE_COM_TIPOS_MISTOS"));

        // nada foi confirmado - operacao atomica
        mockMvc.perform(get("/v1/lancamentos?tipo=DESPESA&status=PENDENTE")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].id").value(despesa.id().toString()));
    }

    private LancamentoResponse criarDespesa(String token, String ambienteId, String contaBancariaId, String formaPagamento)
            throws Exception {
        StringBuilder corpo = new StringBuilder("{\"tipo\":\"DESPESA\",\"valor\":50.00,\"dataEmissao\":\"2026-08-25\","
                + "\"dataVencimento\":\"2026-08-25\",\"descricao\":\"Conta de luz\"");
        if (contaBancariaId != null) {
            corpo.append(",\"contaBancariaId\":\"").append(contaBancariaId).append("\"");
        }
        if (formaPagamento != null) {
            corpo.append(",\"formaPagamento\":\"").append(formaPagamento).append("\"");
        }
        corpo.append("}");
        return postJson("/v1/lancamentos", corpo.toString(), token, ambienteId, LancamentoResponse.class, 201);
    }

    private <T> T postJson(String uri, String corpo, String tokenBearer, String ambienteFinanceiroId,
            Class<T> tipoResposta, int statusEsperado) throws Exception {
        var requestBuilder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(uri)
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
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
