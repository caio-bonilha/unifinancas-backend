package br.com.unifinancas.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.TipoAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.domain.usuario.Usuario;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Valida a cadeia completa (SecurityConfig + TenantContextFilter +
 * TenantContextArgumentResolver) contra um Postgres real, com um controller
 * de teste dedicado - nenhum endpoint real ainda existe (T1.1.5/T1.1.6).
 */
@AutoConfigureMockMvc
@Import(TenantContextIT.ControladorDeTeste.class)
class TenantContextIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void semAuthorizationVira401NaoAutenticado() throws Exception {
        mockMvc.perform(get("/v1/teste/protegido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void comTokenValidoSemHeaderDeAmbienteVira403AmbienteSemVinculo() throws Exception {
        UUID usuarioId = criarUsuarioPersistido();
        String accessToken = jwtTokenService.emitirParDeTokens(usuarioId).accessToken();

        mockMvc.perform(get("/v1/teste/protegido").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("AMBIENTE_SEM_VINCULO"));
    }

    @Test
    void comVinculoAtivoRetorna200EInjetaOTenantContextNoController() throws Exception {
        UUID usuarioId = criarUsuarioPersistido();
        UUID ambienteId = criarAmbientePersistido();
        criarVinculoAtivoPersistido(usuarioId, ambienteId);
        String accessToken = jwtTokenService.emitirParDeTokens(usuarioId).accessToken();

        mockMvc.perform(get("/v1/teste/protegido")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Ambiente-Financeiro-Id", ambienteId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(usuarioId.toString()))
                .andExpect(jsonPath("$.ambienteFinanceiroId").value(ambienteId.toString()))
                .andExpect(jsonPath("$.perfil").value("OWNER"));
    }

    @Test
    void rotaDeAuthNaoExigeAutenticacao() throws Exception {
        mockMvc.perform(get("/v1/auth/ping"))
                .andExpect(status().isOk());
    }

    private UUID criarUsuarioPersistido() {
        Usuario usuario = Usuario.novo("Usuario Teste", UUID.randomUUID() + "@example.com", "hash");
        entityManager.persist(usuario);
        entityManager.flush();
        return usuario.getId();
    }

    private UUID criarAmbientePersistido() {
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Ambiente Teste", TipoAmbienteFinanceiro.PESSOAL);
        entityManager.persist(ambiente);
        entityManager.flush();
        return ambiente.getId();
    }

    private void criarVinculoAtivoPersistido(UUID usuarioId, UUID ambienteId) {
        entityManager.persist(UsuarioAmbienteFinanceiro.primeiroVinculo(usuarioId, ambienteId));
        entityManager.flush();
    }

    @RestController
    static class ControladorDeTeste {

        @GetMapping("/v1/teste/protegido")
        TenantContext protegido(TenantContext contexto) {
            return contexto;
        }

        @GetMapping("/v1/auth/ping")
        String ping() {
            return "pong";
        }
    }
}
