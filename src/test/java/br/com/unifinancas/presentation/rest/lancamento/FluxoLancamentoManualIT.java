package br.com.unifinancas.presentation.rest.lancamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.application.auditoria.AcaoAuditoria;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.OrigemLancamento;
import br.com.unifinancas.domain.lancamento.StatusLancamento;
import br.com.unifinancas.domain.movimentacao.TipoMovimentacao;
import br.com.unifinancas.infrastructure.persistence.jpa.Auditoria;
import br.com.unifinancas.infrastructure.persistence.jpa.AuditoriaJpaRepository;
import br.com.unifinancas.presentation.dto.CadastroResponse;
import br.com.unifinancas.presentation.dto.CategoriaResponse;
import br.com.unifinancas.presentation.dto.ContaBancariaResponse;
import br.com.unifinancas.presentation.dto.LancamentoResponse;
import br.com.unifinancas.presentation.dto.TransferenciaResponse;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T1.3.8, SAD Sec15.1/Sec15.2, Fluxo 6.2 completo: cria um Lancamento
 * manual atraves dos controllers reais (MockMvc, sem subir HTTP de
 * verdade) contra um Postgres real (Testcontainers) e confere as duas
 * reacoes paralelas do diagrama de sequencia que ja existem nesta fase -
 * o evento {@code LancamentoCriadoEvent} (T1.3.4) gravando auditoria
 * (T1.3.7). {@code OrcamentoMetaService} ainda nao existe (T1.5.3), entao
 * nao ha o que conferir desse lado ainda.
 */
@AutoConfigureMockMvc
class FluxoLancamentoManualIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LancamentoRepository lancamentoRepository;

    @Autowired
    private AuditoriaJpaRepository auditoriaJpaRepository;

    @Test
    void fluxo62_criarLancamentoManualPersisteEPublicaEventoQueGeraAuditoria() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Iris Fluxo\",\"email\":\"iris.fluxo62@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();

        CategoriaResponse categoria = postJson("/v1/categorias", "{\"nome\":\"Aluguel\",\"tipo\":\"DESPESA\"}", token,
                ambienteId, CategoriaResponse.class, 201);
        ContaBancariaResponse conta = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Conta Corrente\",\"tipo\":\"CORRENTE\",\"saldoInicial\":1000.00}", token, ambienteId,
                ContaBancariaResponse.class, 201);

        String corpoLancamento = "{\"tipo\":\"DESPESA\",\"valor\":250.00,\"dataEmissao\":\"2026-08-25\","
                + "\"dataVencimento\":\"2026-08-25\",\"categoriaId\":\"" + categoria.id() + "\",\"contaBancariaId\":\""
                + conta.id() + "\",\"observacao\":\"Fluxo 6.2 IT\"}";
        LancamentoResponse lancamento =
                postJson("/v1/lancamentos", corpoLancamento, token, ambienteId, LancamentoResponse.class, 201);

        // 1. Persistencia: o Lancamento existe no banco com os dados corretos (Sec4.7)
        LancamentoFinanceiro persistido = lancamentoRepository.findById(lancamento.id()).orElseThrow();
        assertThat(persistido.getAmbienteFinanceiroId().toString()).isEqualTo(ambienteId);
        assertThat(persistido.getStatus()).isEqualTo(StatusLancamento.PENDENTE);
        assertThat(persistido.getOrigem()).isEqualTo(OrigemLancamento.MANUAL);
        assertThat(persistido.getValor()).isEqualByComparingTo("250.00");
        assertThat(persistido.getCategoriaId()).isEqualTo(categoria.id());
        assertThat(persistido.getContaBancariaId()).isEqualTo(conta.id());

        // 2. Evento de dominio (T1.3.4) -> AuditoriaService (T1.3.7): auditoria gravada apos o commit
        Optional<Auditoria> registro = auditoriaJpaRepository.findAll().stream()
                .filter(a -> lancamento.id().equals(a.getEntidadeId()))
                .findFirst();
        assertThat(registro).isPresent();
        assertThat(registro.get().getEntidade()).isEqualTo("lancamento");
        assertThat(registro.get().getAcao()).isEqualTo(AcaoAuditoria.CRIACAO);
        assertThat(registro.get().getAmbienteFinanceiroId().toString()).isEqualTo(ambienteId);
        assertThat(registro.get().getDadosAntes()).isNull();
        assertThat(registro.get().getDadosDepois()).contains("250.00").contains("DESPESA");

        // 3. RN-06: a visao de "contas a pagar" (GET com filtro) enxerga o Lancamento recem-criado
        mockMvc.perform(get("/v1/lancamentos?tipo=DESPESA&status=PENDENTE,ATRASADO")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].id").value(lancamento.id().toString()));
    }

    /**
     * [Revisado - ADR-012] Transferencia deixou de ser um Lancamento (RN-05
     * antiga) - a RN-05 atual e testada no novo endpoint
     * {@code POST /v1/movimentacoes/transferencias}.
     */
    @Test
    void adr012_rn05_transferenciaParaAMesmaContaVira422EmTodoOStack() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Joao Fluxo\",\"email\":\"joao.fluxo62@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();
        ContaBancariaResponse conta = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Conta Unica\",\"tipo\":\"CORRENTE\",\"saldoInicial\":0}", token, ambienteId,
                ContaBancariaResponse.class, 201);

        String corpo = "{\"contaBancariaOrigemId\":\"" + conta.id() + "\",\"contaBancariaDestinoId\":\"" + conta.id()
                + "\",\"valor\":50.00,\"data\":\"2026-08-25\",\"descricao\":\"Reserva\"}";

        mockMvc.perform(post("/v1/movimentacoes/transferencias")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSFERENCIA_MESMA_CONTA"));
    }

    /** ADR-012: fluxo feliz da Transferencia - cria as duas Movimentacoes e atualiza o saldo das duas contas. */
    @Test
    void adr012_transferenciaComReferenciasValidasAtualizaOSaldoDasDuasContas() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Ana Transferencia\",\"email\":\"ana.transferencia@example.com\",\"senha\":\"senha12345\"}",
                null, null, CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();
        ContaBancariaResponse origem = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Corrente\",\"tipo\":\"CORRENTE\",\"saldoInicial\":500.00}", token, ambienteId,
                ContaBancariaResponse.class, 201);
        ContaBancariaResponse destino = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Poupanca\",\"tipo\":\"POUPANCA\",\"saldoInicial\":100.00}", token, ambienteId,
                ContaBancariaResponse.class, 201);

        String corpo = "{\"contaBancariaOrigemId\":\"" + origem.id() + "\",\"contaBancariaDestinoId\":\"" + destino.id()
                + "\",\"valor\":80.00,\"data\":\"2026-08-25\",\"descricao\":\"Reserva mensal\"}";

        TransferenciaResponse transferencia = postJson("/v1/movimentacoes/transferencias", corpo, token, ambienteId,
                TransferenciaResponse.class, 201);

        assertThat(transferencia.origem().tipo()).isEqualTo(TipoMovimentacao.DEBITO);
        assertThat(transferencia.origem().contaBancariaId()).isEqualTo(origem.id());
        assertThat(transferencia.destino().tipo()).isEqualTo(TipoMovimentacao.CREDITO);
        assertThat(transferencia.destino().contaBancariaId()).isEqualTo(destino.id());
        assertThat(transferencia.origem().grupoTransferenciaId()).isNotNull()
                .isEqualTo(transferencia.destino().grupoTransferenciaId());
        assertThat(transferencia.origem().lancamentoId()).isNull();

        ContaBancariaResponse[] contas = objectMapper.readValue(
                mockMvc.perform(get("/v1/contas-bancarias")
                                .header("Authorization", token)
                                .header("X-Ambiente-Financeiro-Id", ambienteId))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                ContaBancariaResponse[].class);

        ContaBancariaResponse origemAtualizada = java.util.Arrays.stream(contas)
                .filter(c -> c.id().equals(origem.id())).findFirst().orElseThrow();
        ContaBancariaResponse destinoAtualizada = java.util.Arrays.stream(contas)
                .filter(c -> c.id().equals(destino.id())).findFirst().orElseThrow();
        assertThat(origemAtualizada.saldoAtual()).isEqualByComparingTo("420.00");
        assertThat(destinoAtualizada.saldoAtual()).isEqualByComparingTo("180.00");

        mockMvc.perform(get("/v1/contas-bancarias/" + origem.id() + "/movimentacoes")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].tipo").value("DEBITO"))
                .andExpect(jsonPath("$.itens[0].valor").value(80.00));
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
