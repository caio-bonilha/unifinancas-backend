package br.com.unifinancas.presentation.rest.ambiente;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.application.ambientefinanceiro.AmbienteFinanceiroResumo;
import br.com.unifinancas.application.ambientefinanceiro.AmbienteFinanceiroService;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.TipoAmbienteFinanceiro;
import br.com.unifinancas.presentation.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AmbienteController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AmbienteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AmbienteFinanceiroService ambienteFinanceiroService;

    @Test
    void listarRetornaOsAmbientesDoUsuarioAutenticado() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID ambienteId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(usuarioId, null, List.of()));
        when(ambienteFinanceiroService.listarAmbientesAtivosDoUsuario(usuarioId)).thenReturn(List.of(
                new AmbienteFinanceiroResumo(ambienteId, "Pessoal", TipoAmbienteFinanceiro.PESSOAL, PerfilUsuarioAmbiente.OWNER, 3)));

        try {
            mockMvc.perform(get("/v1/ambientes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(ambienteId.toString()))
                    .andExpect(jsonPath("$[0].nome").value("Pessoal"))
                    .andExpect(jsonPath("$[0].tipo").value("PESSOAL"))
                    .andExpect(jsonPath("$[0].perfil").value("OWNER"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void criarRetorna201ComOAmbienteCriadoComoOwner() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID ambienteId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(usuarioId, null, List.of()));
        when(ambienteFinanceiroService.criar(eq(usuarioId), eq("Projeto Paralelo"), eq(TipoAmbienteFinanceiro.PROJETO), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new AmbienteFinanceiroResumo(ambienteId, "Projeto Paralelo", TipoAmbienteFinanceiro.PROJETO,
                        PerfilUsuarioAmbiente.OWNER, 3));

        try {
            mockMvc.perform(post("/v1/ambientes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\":\"Projeto Paralelo\",\"tipo\":\"PROJETO\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(ambienteId.toString()))
                    .andExpect(jsonPath("$.nome").value("Projeto Paralelo"))
                    .andExpect(jsonPath("$.tipo").value("PROJETO"))
                    .andExpect(jsonPath("$.perfil").value("OWNER"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void criarSemNomeVira422() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(usuarioId, null, List.of()));

        try {
            mockMvc.perform(post("/v1/ambientes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\":\"\",\"tipo\":\"PROJETO\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void criarSemTipoVira422() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(usuarioId, null, List.of()));

        try {
            mockMvc.perform(post("/v1/ambientes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\":\"Projeto Paralelo\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
