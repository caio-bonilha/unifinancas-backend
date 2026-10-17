package br.com.unifinancas.presentation.rest.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.presentation.dto.CadastroResponse;
import br.com.unifinancas.presentation.dto.ContaBancariaResponse;
import br.com.unifinancas.presentation.dto.PaginaLancamentosResponse;
import br.com.unifinancas.presentation.dto.TabelaImportadaResponse;
import br.com.unifinancas.presentation.dto.TransacaoAnalisadaResponse;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * RF-21, RNF-02, T4.1.1-T4.1.4: fluxo completo de importacao via HTTP -
 * upload OFX -> analisar (sem duplicata) -> confirmar (Lancamento PAGO) ->
 * reimportar o mesmo arquivo (agora detecta duplicata); e o fluxo CSV
 * (tabela -> analisar -> confirmar). Cobertura de regra de negocio ja a
 * fundo em ImportacaoServiceTest/LancamentoServiceTest - aqui so a
 * integracao ponta a ponta via HTTP (controllers reais, MockMvc, Postgres
 * real via Testcontainers).
 */
@AutoConfigureMockMvc
class FluxoImportacaoIT extends AbstractIntegrationTest {

    private static final String OFX = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102
            CHARSET:USASCII

            <OFX>
            <BANKMSGSRSV1>
            <STMTTRNRS>
            <STMTRS>
            <CURDEF>BRL
            <BANKACCTFROM>
            <BANKID>001
            <ACCTID>12345-6
            </BANKACCTFROM>
            <BANKTRANLIST>
            <STMTTRN>
            <TRNTYPE>DEBIT
            <DTPOSTED>20260805120000
            <TRNAMT>-45.90
            <FITID>2026080500001
            <NAME>Padaria
            </STMTTRN>
            </BANKTRANLIST>
            </STMTRS>
            </STMTTRNRS>
            </BANKMSGSRSV1>
            </OFX>
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void importaOfxEDetectaDuplicataAoReimportar() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Renata Importa\",\"email\":\"renata.importa@example.com\",\"senha\":\"senha12345\"}",
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();

        ContaBancariaResponse conta = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Nubank\",\"tipo\":\"CORRENTE\",\"saldoInicial\":100.00}", token, ambienteId,
                ContaBancariaResponse.class, 201);

        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "extrato.ofx", MediaType.TEXT_PLAIN_VALUE, OFX.getBytes());
        MvcResult resultadoOfx = mockMvc.perform(multipart("/v1/importacoes/ofx")
                        .file(arquivo)
                        .param("contaBancariaId", conta.id().toString())
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andReturn();
        TransacaoAnalisadaResponse[] analisadas =
                objectMapper.readValue(resultadoOfx.getResponse().getContentAsString(), TransacaoAnalisadaResponse[].class);
        assertThat(analisadas).hasSize(1);
        assertThat(analisadas[0].duplicada()).isFalse();
        assertThat(analisadas[0].valor()).isEqualByComparingTo("-45.90");
        String identificador = analisadas[0].identificadorExterno();
        assertThat(identificador).isEqualTo("2026080500001");

        String corpoConfirmar = "{\"contaBancariaId\":\"" + conta.id() + "\",\"transacoes\":[{"
                + "\"data\":\"" + analisadas[0].data() + "\",\"valor\":-45.90,\"descricao\":\"Padaria\","
                + "\"identificadorExterno\":\"" + identificador + "\"}]}";
        mockMvc.perform(post("/v1/importacoes")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoConfirmar))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].status").value("PAGO"))
                .andExpect(jsonPath("$[0].origem").value("IMPORTACAO"));

        // reimporta o mesmo arquivo - agora detecta duplicata
        MockMultipartFile arquivo2 = new MockMultipartFile("arquivo", "extrato.ofx", MediaType.TEXT_PLAIN_VALUE, OFX.getBytes());
        MvcResult resultadoReimportacao = mockMvc.perform(multipart("/v1/importacoes/ofx")
                        .file(arquivo2)
                        .param("contaBancariaId", conta.id().toString())
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andReturn();
        TransacaoAnalisadaResponse[] reanalisadas =
                objectMapper.readValue(resultadoReimportacao.getResponse().getContentAsString(), TransacaoAnalisadaResponse[].class);
        assertThat(reanalisadas[0].duplicada()).isTrue();
        assertThat(reanalisadas[0].lancamentoDuplicadoId()).isNotNull();

        // saldo da conta reflete a Movimentacao criada pela confirmacao (100.00 - 45.90)
        MvcResult resultadoConta = mockMvc.perform(get("/v1/contas-bancarias")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andReturn();
        ContaBancariaResponse[] contas =
                objectMapper.readValue(resultadoConta.getResponse().getContentAsString(), ContaBancariaResponse[].class);
        assertThat(contas[0].saldoAtual()).isEqualByComparingTo("54.10");
    }

    @Test
    void importaCsvViaFluxoDeTabelaEAnalise() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Bruno Importa\",\"email\":\"bruno.importa@example.com\",\"senha\":\"senha12345\"}",
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();

        ContaBancariaResponse conta = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Itau\",\"tipo\":\"CORRENTE\",\"saldoInicial\":0}", token, ambienteId,
                ContaBancariaResponse.class, 201);

        String csv = "Data,Descricao,Valor\n05/08/2026,Salario,1500.00\n";
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "extrato.csv", "text/csv", csv.getBytes());
        MvcResult resultadoTabela = mockMvc.perform(multipart("/v1/importacoes/tabela")
                        .file(arquivo)
                        .param("contaBancariaId", conta.id().toString())
                        .param("tipoArquivo", "CSV")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andReturn();
        TabelaImportadaResponse tabela =
                objectMapper.readValue(resultadoTabela.getResponse().getContentAsString(), TabelaImportadaResponse.class);
        assertThat(tabela.cabecalho()).containsExactly("Data", "Descricao", "Valor");
        assertThat(tabela.linhas()).hasSize(1);

        String corpoAnalisar = "{\"contaBancariaId\":\"" + conta.id() + "\",\"transacoes\":[{"
                + "\"data\":\"2026-08-05\",\"valor\":1500.00,\"descricao\":\"Salario\"}]}";
        MvcResult resultadoAnalise = mockMvc.perform(post("/v1/importacoes/analisar")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAnalisar))
                .andExpect(status().isOk())
                .andReturn();
        TransacaoAnalisadaResponse[] analisadas =
                objectMapper.readValue(resultadoAnalise.getResponse().getContentAsString(), TransacaoAnalisadaResponse[].class);
        assertThat(analisadas[0].duplicada()).isFalse();
        assertThat(analisadas[0].identificadorExterno()).isNotBlank();

        String corpoConfirmar = "{\"contaBancariaId\":\"" + conta.id() + "\",\"transacoes\":[{"
                + "\"data\":\"2026-08-05\",\"valor\":1500.00,\"descricao\":\"Salario\","
                + "\"identificadorExterno\":\"" + analisadas[0].identificadorExterno() + "\"}]}";
        mockMvc.perform(post("/v1/importacoes")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoConfirmar))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].status").value("RECEBIDO"))
                .andExpect(jsonPath("$[0].tipo").value("RECEITA"));

        MvcResult resultadoLancamentos = mockMvc.perform(get("/v1/lancamentos")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andReturn();
        PaginaLancamentosResponse pagina = objectMapper.readValue(
                resultadoLancamentos.getResponse().getContentAsString(), PaginaLancamentosResponse.class);
        assertThat(pagina.itens()).anySatisfy(l -> assertThat(l.origem().name()).isEqualTo("IMPORTACAO"));
    }

    @Test
    void arquivoOfxInvalidoRetornaRegraDeNegocio() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Carla Importa\",\"email\":\"carla.importa@example.com\",\"senha\":\"senha12345\"}",
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();
        ContaBancariaResponse conta = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Conta\",\"tipo\":\"CORRENTE\",\"saldoInicial\":0}", token, ambienteId,
                ContaBancariaResponse.class, 201);

        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "invalido.ofx", MediaType.TEXT_PLAIN_VALUE, "nao e ofx".getBytes());
        mockMvc.perform(multipart("/v1/importacoes/ofx")
                        .file(arquivo)
                        .param("contaBancariaId", conta.id().toString())
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("ARQUIVO_INVALIDO"));
    }

    private <T> T postJson(String uri, String corpo, Class<T> tipoResposta, int statusEsperado) throws Exception {
        return postJson(uri, corpo, null, null, tipoResposta, statusEsperado);
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
