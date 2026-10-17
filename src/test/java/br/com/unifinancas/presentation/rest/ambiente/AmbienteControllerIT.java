package br.com.unifinancas.presentation.rest.ambiente;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.application.usuario.IdentidadeService;
import br.com.unifinancas.application.usuario.ResultadoCadastro;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Valida GET/POST /v1/ambientes ponta a ponta (Testcontainers): sem header de ambiente, so autenticacao. */
@AutoConfigureMockMvc
class AmbienteControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentidadeService identidadeService;

    @Test
    void listaOAmbientePessoalCriadoNoCadastroSemPrecisarDoHeaderDeAmbiente() throws Exception {
        ResultadoCadastro cadastro = identidadeService.registrar("Ana Silva", "ana.ambientes@example.com", "senha12345");

        mockMvc.perform(get("/v1/ambientes").header("Authorization", "Bearer " + cadastro.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(cadastro.ambienteFinanceiroId().toString()))
                .andExpect(jsonPath("$[0].tipo").value("PESSOAL"))
                .andExpect(jsonPath("$[0].perfil").value("OWNER"));
    }

    @Test
    void semAuthorizationVira401() throws Exception {
        mockMvc.perform(get("/v1/ambientes")).andExpect(status().isUnauthorized());
    }

    @Test
    void criaUmSegundoAmbienteEDepoisApareceNaListagem() throws Exception {
        ResultadoCadastro cadastro = identidadeService.registrar("Bruno Silva", "bruno.ambientes@example.com", "senha12345");
        String token = "Bearer " + cadastro.accessToken();

        mockMvc.perform(post("/v1/ambientes")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Projeto Paralelo\",\"tipo\":\"PROJETO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Projeto Paralelo"))
                .andExpect(jsonPath("$.tipo").value("PROJETO"))
                .andExpect(jsonPath("$.perfil").value("OWNER"));

        mockMvc.perform(get("/v1/ambientes").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
