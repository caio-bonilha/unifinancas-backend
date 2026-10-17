package br.com.unifinancas.presentation.rest.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.presentation.dto.AmbienteResponse;
import br.com.unifinancas.presentation.dto.CadastroResponse;
import br.com.unifinancas.presentation.dto.ContaBancariaResponse;
import br.com.unifinancas.presentation.dto.ConviteResponse;
import br.com.unifinancas.presentation.dto.ExportacaoDadosResponse;
import br.com.unifinancas.presentation.dto.LancamentoResponse;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T1.11.4, SAD Sec15.1/15.2, RNF-09 (LGPD - portabilidade e direito ao
 * esquecimento, RN-13): fluxo completo de exportacao e exclusao de dados
 * atraves dos controllers reais (MockMvc, Postgres via Testcontainers).
 */
@AutoConfigureMockMvc
class PrivacidadeIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exportacaoSoTrazAmbienteOndeEOwnerEExclusaoEncerraEsseAmbienteERemoveOMembro() throws Exception {
        // 1. Ana se cadastra (ganha o Ambiente PESSOAL como OWNER) e cria dados nele
        CadastroResponse cadastroAna = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Ana Privacidade\",\"email\":\"ana.privacidade@example.com\",\"senha\":\"senha12345\"}",
                null, null, CadastroResponse.class, 201);
        String tokenAna = "Bearer " + cadastroAna.accessToken();
        String ambientePessoalId = cadastroAna.ambienteFinanceiroId().toString();

        ContaBancariaResponse conta = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Conta Corrente\",\"tipo\":\"CORRENTE\",\"saldoInicial\":100.00}", tokenAna,
                ambientePessoalId, ContaBancariaResponse.class, 201);
        LancamentoResponse lancamento = postJson("/v1/lancamentos",
                "{\"tipo\":\"DESPESA\",\"valor\":50.00,\"dataEmissao\":\"2026-08-25\","
                        + "\"dataVencimento\":\"2026-08-25\",\"contaBancariaId\":\"" + conta.id()
                        + "\",\"descricao\":\"Despesa de Ana\"}",
                tokenAna, ambientePessoalId, LancamentoResponse.class, 201);

        // 2. Ana cria um segundo Ambiente (FAMILIAR) e convida Bruno como MEMBRO
        AmbienteResponse ambienteFamiliar =
                postJson("/v1/ambientes", "{\"nome\":\"Familia Privacidade\",\"tipo\":\"FAMILIAR\"}", tokenAna, null,
                        AmbienteResponse.class, 201);
        CadastroResponse cadastroBruno = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Bruno Privacidade\",\"email\":\"bruno.privacidade@example.com\",\"senha\":\"senha12345\"}",
                null, null, CadastroResponse.class, 201);
        String tokenBruno = "Bearer " + cadastroBruno.accessToken();

        ConviteResponse convite = postJson("/v1/ambientes/" + ambienteFamiliar.id() + "/convites",
                "{\"email\":\"bruno.privacidade@example.com\",\"perfil\":\"MEMBRO\"}", tokenAna,
                ambienteFamiliar.id().toString(), ConviteResponse.class, 201);
        mockMvc.perform(post("/v1/ambientes/" + ambienteFamiliar.id() + "/convites/aceitar")
                        .header("Authorization", tokenBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + convite.token() + "\"}"))
                .andExpect(status().isNoContent());

        // 3. Exportacao (RNF-09): so o Ambiente PESSOAL (onde Ana e OWNER) aparece - o FAMILIAR tambem, ja que ela e OWNER dos dois
        ExportacaoDadosResponse exportacao =
                objectMapper.readValue(
                        mockMvc.perform(get("/v1/usuarios/me/exportacao").header("Authorization", tokenAna))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString(),
                        ExportacaoDadosResponse.class);
        assertThat(exportacao.usuario().email()).isEqualTo("ana.privacidade@example.com");
        assertThat(exportacao.ambientes()).extracting(a -> a.id().toString())
                .containsExactlyInAnyOrder(ambientePessoalId, ambienteFamiliar.id().toString());
        var ambientePessoalExportado = exportacao.ambientes().stream()
                .filter(a -> a.id().toString().equals(ambientePessoalId)).findFirst().orElseThrow();
        assertThat(ambientePessoalExportado.contasBancarias()).extracting(c -> c.id().toString())
                .containsExactly(conta.id().toString());
        assertThat(ambientePessoalExportado.lancamentos()).extracting(l -> l.id().toString())
                .containsExactly(lancamento.id().toString());

        // 4. Exclusao da conta de Ana (RN-13): 204, sem corpo
        mockMvc.perform(delete("/v1/usuarios/me").header("Authorization", tokenAna))
                .andExpect(status().isNoContent());

        // 5. Login de Ana passa a falhar - a conta esta EXCLUIDO, nao ATIVO
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana.privacidade@example.com\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));

        // 6. Nenhum Ambiente sobrevive para Ana (todos os vinculos foram removidos, inclusive os dois onde ela era OWNER)
        mockMvc.perform(get("/v1/ambientes").header("Authorization", tokenAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 7. Bruno tambem perde acesso ao Ambiente FAMILIAR - foi encerrado junto com a exclusao do OWNER (Ana)
        mockMvc.perform(get("/v1/ambientes/" + ambienteFamiliar.id() + "/membros")
                        .header("Authorization", tokenBruno)
                        .header("X-Ambiente-Financeiro-Id", ambienteFamiliar.id().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("AMBIENTE_SEM_VINCULO"));

        // 8. Excluir de novo (token de acesso antigo ainda nao expirou) e um erro, nao uma repeticao silenciosa
        mockMvc.perform(delete("/v1/usuarios/me").header("Authorization", tokenAna))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USUARIO_JA_EXCLUIDO"));
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
