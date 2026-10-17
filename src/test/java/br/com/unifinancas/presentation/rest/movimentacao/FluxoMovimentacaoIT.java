package br.com.unifinancas.presentation.rest.movimentacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.presentation.dto.CadastroResponse;
import br.com.unifinancas.presentation.dto.ContaBancariaResponse;
import br.com.unifinancas.presentation.dto.LancamentoResponse;
import br.com.unifinancas.presentation.dto.MovimentacaoResponse;
import br.com.unifinancas.presentation.dto.TransferenciaResponse;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * ADR-014, T1.3b.6: fluxo completo de Movimentacao avulsa (criar/editar/
 * excluir) e da exclusao unificada (desfaz a origem) - LIQUIDACAO reabre o
 * Lancamento, TRANSFERENCIA apaga o par - atraves dos controllers reais
 * (MockMvc) contra um Postgres real (Testcontainers).
 */
@AutoConfigureMockMvc
class FluxoMovimentacaoIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void criarEditarEExcluirUmaMovimentacaoAvulsaAtualizaOSaldoACadaPasso() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Marta Avulsa\",\"email\":\"marta.avulsa@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();
        ContaBancariaResponse conta = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Corrente\",\"tipo\":\"CORRENTE\",\"saldoInicial\":1000.00}", token, ambienteId,
                ContaBancariaResponse.class, 201);

        // 1. Criar avulsa (DEBITO) - saldo cai
        String corpoCriar = "{\"contaBancariaId\":\"" + conta.id() + "\",\"tipo\":\"DEBITO\",\"valor\":12.90,"
                + "\"data\":\"2026-08-25\",\"descricao\":\"Tarifa de manutenção\"}";
        MovimentacaoResponse movimentacao = postJson("/v1/movimentacoes", corpoCriar, token, ambienteId,
                MovimentacaoResponse.class, 201);
        assertThat(movimentacao.origem().name()).isEqualTo("MANUAL");
        assertThat(saldoAtual(token, ambienteId, conta.id())).isEqualByComparingTo("987.10");

        // 2. Editar - muda o valor, saldo recalcula de novo
        String corpoEditar = "{\"tipo\":\"DEBITO\",\"valor\":20.00,\"data\":\"2026-08-25\","
                + "\"descricao\":\"Tarifa corrigida\"}";
        mockMvc.perform(put("/v1/movimentacoes/" + movimentacao.id())
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEditar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(20.00))
                .andExpect(jsonPath("$.descricao").value("Tarifa corrigida"));
        assertThat(saldoAtual(token, ambienteId, conta.id())).isEqualByComparingTo("980.00");

        // 3. Excluir - saldo volta ao original
        mockMvc.perform(delete("/v1/movimentacoes/" + movimentacao.id())
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isNoContent());
        assertThat(saldoAtual(token, ambienteId, conta.id())).isEqualByComparingTo("1000.00");
    }

    /** ADR-014: PUT numa Movimentacao de LIQUIDACAO/TRANSFERENCIA e recusado (imutavel). */
    @Test
    void editarUmaMovimentacaoDeLiquidacaoVira422EmTodoOStack() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Nina Liquidacao\",\"email\":\"nina.liquidacao@example.com\",\"senha\":\"senha12345\"}",
                null, null, CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();
        ContaBancariaResponse conta = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Corrente\",\"tipo\":\"CORRENTE\",\"saldoInicial\":500.00}", token, ambienteId,
                ContaBancariaResponse.class, 201);
        LancamentoResponse lancamento = criarEPagarLancamento(token, ambienteId, conta.id());
        MovimentacaoResponse movimentacao = movimentacaoDoLancamento(token, ambienteId, conta.id(), lancamento.id());

        String corpoEditar = "{\"tipo\":\"DEBITO\",\"valor\":999.00,\"data\":\"2026-08-25\",\"descricao\":\"Tentativa\"}";
        mockMvc.perform(put("/v1/movimentacoes/" + movimentacao.id())
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEditar))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("MOVIMENTACAO_NAO_EDITAVEL"));
    }

    /** ADR-014: excluir a Movimentacao de um Lancamento pago reabre o Lancamento e recalcula o saldo. */
    @Test
    void excluirUmaMovimentacaoDeLiquidacaoReabreOLancamentoERecalculaOSaldo() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Paulo Liquidacao\",\"email\":\"paulo.liquidacao@example.com\",\"senha\":\"senha12345\"}",
                null, null, CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();
        ContaBancariaResponse conta = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Corrente\",\"tipo\":\"CORRENTE\",\"saldoInicial\":500.00}", token, ambienteId,
                ContaBancariaResponse.class, 201);
        LancamentoResponse lancamento = criarEPagarLancamento(token, ambienteId, conta.id());
        assertThat(saldoAtual(token, ambienteId, conta.id())).isEqualByComparingTo("250.00");
        MovimentacaoResponse movimentacao = movimentacaoDoLancamento(token, ambienteId, conta.id(), lancamento.id());

        mockMvc.perform(delete("/v1/movimentacoes/" + movimentacao.id())
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isNoContent());

        // Lancamento voltou a PENDENTE (vencimento no futuro) e o saldo foi restaurado
        mockMvc.perform(get("/v1/lancamentos?tipo=DESPESA&status=PENDENTE")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].id").value(lancamento.id().toString()));
        assertThat(saldoAtual(token, ambienteId, conta.id())).isEqualByComparingTo("500.00");
    }

    /** ADR-014: excluir uma perna da Transferencia apaga as duas e recalcula o saldo das duas contas. */
    @Test
    void excluirUmaMovimentacaoDeTransferenciaApagaAsDuasPernasERecalculaOsDoisSaldos() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Rita Transferencia\",\"email\":\"rita.transferencia2@example.com\",\"senha\":\"senha12345\"}",
                null, null, CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();
        ContaBancariaResponse origem = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Corrente\",\"tipo\":\"CORRENTE\",\"saldoInicial\":500.00}", token, ambienteId,
                ContaBancariaResponse.class, 201);
        ContaBancariaResponse destino = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Poupanca\",\"tipo\":\"POUPANCA\",\"saldoInicial\":100.00}", token, ambienteId,
                ContaBancariaResponse.class, 201);
        String corpoTransferencia = "{\"contaBancariaOrigemId\":\"" + origem.id() + "\",\"contaBancariaDestinoId\":\""
                + destino.id() + "\",\"valor\":80.00,\"data\":\"2026-08-25\",\"descricao\":\"Reserva mensal\"}";
        TransferenciaResponse transferencia = postJson("/v1/movimentacoes/transferencias", corpoTransferencia, token,
                ambienteId, TransferenciaResponse.class, 201);
        assertThat(saldoAtual(token, ambienteId, origem.id())).isEqualByComparingTo("420.00");
        assertThat(saldoAtual(token, ambienteId, destino.id())).isEqualByComparingTo("180.00");

        // Exclui so o lado do credito (destino) - decisao consultada com o usuario: e entendido como "devolver" a transferencia
        mockMvc.perform(delete("/v1/movimentacoes/" + transferencia.destino().id())
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isNoContent());

        assertThat(saldoAtual(token, ambienteId, origem.id())).isEqualByComparingTo("500.00");
        assertThat(saldoAtual(token, ambienteId, destino.id())).isEqualByComparingTo("100.00");
        mockMvc.perform(get("/v1/contas-bancarias/" + origem.id() + "/movimentacoes")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty());
    }

    /** Cria um Lancamento DESPESA de 250,00 vinculado a conta e confirma o pagamento (gera a Movimentacao LIQUIDACAO). */
    private LancamentoResponse criarEPagarLancamento(String token, String ambienteId, java.util.UUID contaId) throws Exception {
        String corpoLancamento = "{\"tipo\":\"DESPESA\",\"valor\":250.00,\"dataEmissao\":\"2026-08-25\","
                + "\"dataVencimento\":\"2026-12-25\",\"contaBancariaId\":\"" + contaId + "\",\"descricao\":\"Aluguel\"}";
        LancamentoResponse lancamento =
                postJson("/v1/lancamentos", corpoLancamento, token, ambienteId, LancamentoResponse.class, 201);

        mockMvc.perform(patch("/v1/lancamentos/" + lancamento.id())
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"CONFIRMAR_PAGAMENTO\",\"dataPagamento\":\"2026-08-25\"}"))
                .andExpect(status().isOk());
        return lancamento;
    }

    private MovimentacaoResponse movimentacaoDoLancamento(String token, String ambienteId, java.util.UUID contaId,
            java.util.UUID lancamentoId) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/v1/contas-bancarias/" + contaId + "/movimentacoes")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andReturn();
        MovimentacaoResponse[] itens = objectMapper.readTree(resultado.getResponse().getContentAsString())
                .get("itens").traverse(objectMapper).readValueAs(MovimentacaoResponse[].class);
        return Arrays.stream(itens).filter(m -> lancamentoId.equals(m.lancamentoId())).findFirst().orElseThrow();
    }

    private java.math.BigDecimal saldoAtual(String token, String ambienteId, java.util.UUID contaId) throws Exception {
        ContaBancariaResponse[] contas = objectMapper.readValue(
                mockMvc.perform(get("/v1/contas-bancarias")
                                .header("Authorization", token)
                                .header("X-Ambiente-Financeiro-Id", ambienteId))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ContaBancariaResponse[].class);
        return Arrays.stream(contas).filter(c -> c.id().equals(contaId)).findFirst().orElseThrow().saldoAtual();
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
