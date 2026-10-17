package br.com.unifinancas.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.presentation.dto.AmbienteResponse;
import br.com.unifinancas.presentation.dto.CadastroResponse;
import br.com.unifinancas.presentation.dto.ConviteResponse;
import br.com.unifinancas.presentation.dto.MembroResponse;
import br.com.unifinancas.presentation.dto.TokensResponse;
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
 * T1.1.9, SAD Sec15.2: fluxo completo cadastro -&gt; login -&gt; troca de
 * ambiente -&gt; convite, de ponta a ponta atraves dos controllers reais
 * (MockMvc, sem subir um servidor HTTP de verdade) contra um Postgres real
 * (Testcontainers, {@link AbstractIntegrationTest}).
 */
@AutoConfigureMockMvc
class FluxoCadastroLoginTrocaConviteIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void cadastroLoginTrocaDeAmbienteEConvite() throws Exception {
        // 1. Cadastro de Ana -> Usuario + Ambiente Pessoal + vinculo OWNER (Fluxo 6.1)
        CadastroResponse cadastroAna = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Ana Fluxo\",\"email\":\"ana.fluxo@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);

        // 2. Login com a mesma credencial - confirma que a autenticacao (nao so o cadastro) emite tokens validos
        TokensResponse loginAna = postJson("/v1/auth/login",
                "{\"email\":\"ana.fluxo@example.com\",\"senha\":\"senha12345\"}", null, null, TokensResponse.class, 200);
        String tokenAna = "Bearer " + loginAna.accessToken();

        // 3. Troca de ambiente: Ana cria um segundo Ambiente Financeiro e passa a ter dois para escolher
        AmbienteResponse ambienteFamiliar = postJson("/v1/ambientes",
                "{\"nome\":\"Familia Fluxo\",\"tipo\":\"FAMILIAR\"}", tokenAna, null, AmbienteResponse.class, 201);

        MvcResult listagem = mockMvc.perform(get("/v1/ambientes").header("Authorization", tokenAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        List<AmbienteResponse> ambientesDeAna = objectMapper.readValue(listagem.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, AmbienteResponse.class));
        assertThat(ambientesDeAna).extracting(AmbienteResponse::id)
                .containsExactlyInAnyOrder(cadastroAna.ambienteFinanceiroId(), ambienteFamiliar.id());

        // 4. Convite: cadastro de Bruno, convidado por Ana para o ambiente FAMILIAR (nao o pessoal)
        CadastroResponse cadastroBruno = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Bruno Fluxo\",\"email\":\"bruno.fluxo@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String tokenBruno = "Bearer " + cadastroBruno.accessToken();

        ConviteResponse convite = postJson("/v1/ambientes/" + ambienteFamiliar.id() + "/convites",
                "{\"email\":\"bruno.fluxo@example.com\",\"perfil\":\"MEMBRO\"}", tokenAna,
                ambienteFamiliar.id().toString(), ConviteResponse.class, 201);

        // Bruno aceita - rota isenta do header de ambiente (ainda nao tem vinculo)
        mockMvc.perform(post("/v1/ambientes/" + ambienteFamiliar.id() + "/convites/aceitar")
                        .header("Authorization", tokenBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + convite.token() + "\"}"))
                .andExpect(status().isNoContent());

        // 5. Bruno agora enxerga o ambiente familiar na propria listagem (alem do seu pessoal)
        mockMvc.perform(get("/v1/ambientes").header("Authorization", tokenBruno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Membros do ambiente familiar: Ana (OWNER) e Bruno (MEMBRO)
        MvcResult membrosResult = mockMvc.perform(get("/v1/ambientes/" + ambienteFamiliar.id() + "/membros")
                        .header("Authorization", tokenAna)
                        .header("X-Ambiente-Financeiro-Id", ambienteFamiliar.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        List<MembroResponse> membros = objectMapper.readValue(membrosResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, MembroResponse.class));
        assertThat(membros).extracting(MembroResponse::email)
                .containsExactlyInAnyOrder("ana.fluxo@example.com", "bruno.fluxo@example.com");

        // 6. Isolamento entre tenants (SAD Sec8.7/Sec12.4): Bruno nao tem vinculo com o ambiente PESSOAL de Ana
        mockMvc.perform(get("/v1/ambientes/" + cadastroAna.ambienteFinanceiroId() + "/membros")
                        .header("Authorization", tokenBruno)
                        .header("X-Ambiente-Financeiro-Id", cadastroAna.ambienteFinanceiroId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("AMBIENTE_SEM_VINCULO"));

        // 7. Ana (OWNER) promove Bruno a ADMIN no ambiente familiar
        var usuarioIdBruno = membros.stream()
                .filter(m -> m.email().equals("bruno.fluxo@example.com"))
                .findFirst().orElseThrow().usuarioId();

        mockMvc.perform(patch("/v1/ambientes/" + ambienteFamiliar.id() + "/membros/" + usuarioIdBruno)
                        .header("Authorization", tokenAna)
                        .header("X-Ambiente-Financeiro-Id", ambienteFamiliar.id().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"ALTERAR_PERFIL\",\"perfil\":\"ADMIN\"}"))
                .andExpect(status().isNoContent());
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
