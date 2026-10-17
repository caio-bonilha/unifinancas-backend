package br.com.unifinancas.presentation.rest.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.application.usuario.IdentidadeService;
import br.com.unifinancas.application.usuario.ResultadoCadastro;
import br.com.unifinancas.application.usuario.ResultadoLogin;
import br.com.unifinancas.domain.shared.RecursoConflitanteException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.presentation.exception.GlobalExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IdentidadeService identidadeService;

    @Test
    void cadastroValidoRetorna201ComTokensEAmbiente() throws Exception {
        UUID ambienteId = UUID.randomUUID();
        when(identidadeService.registrar(anyString(), anyString(), anyString()))
                .thenReturn(new ResultadoCadastro("access-token", "refresh-token", ambienteId, "Maria Silva"));

        mockMvc.perform(post("/v1/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria Silva\",\"email\":\"maria@example.com\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.ambienteFinanceiroId").value(ambienteId.toString()))
                .andExpect(jsonPath("$.nome").value("Maria Silva"));
    }

    @Test
    void senhaCurtaVira422ComDetalheDoCampo() throws Exception {
        mockMvc.perform(post("/v1/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria\",\"email\":\"maria@example.com\",\"senha\":\"curta\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"))
                .andExpect(jsonPath("$.detalhes[0].campo").value("senha"));
    }

    @Test
    void emailInvalidoVira422() throws Exception {
        mockMvc.perform(post("/v1/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria\",\"email\":\"nao-e-email\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"));
    }

    @Test
    void emailJaCadastradoVira409() throws Exception {
        when(identidadeService.registrar(anyString(), anyString(), anyString()))
                .thenThrow(new RecursoConflitanteException("EMAIL_JA_CADASTRADO", "Este e-mail ja esta cadastrado."));

        mockMvc.perform(post("/v1/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria\",\"email\":\"maria@example.com\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_JA_CADASTRADO"));
    }

    @Test
    void loginValidoRetorna200ComTokens() throws Exception {
        when(identidadeService.autenticar(anyString(), anyString()))
                .thenReturn(new ResultadoLogin("access-token", "refresh-token", "Maria Silva"));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"maria@example.com\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.nome").value("Maria Silva"));
    }

    @Test
    void loginComCredenciaisInvalidasVira401() throws Exception {
        when(identidadeService.autenticar(anyString(), anyString()))
                .thenThrow(new BadCredentialsException("E-mail ou senha invalidos."));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"maria@example.com\",\"senha\":\"senha-errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"))
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha invalidos."));
    }

    @Test
    void loginComEmailInvalidoVira422() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-e-email\",\"senha\":\"senha12345\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"));
    }

    @Test
    void refreshValidoRetorna200ComNovosTokens() throws Exception {
        when(identidadeService.renovar("token-antigo"))
                .thenReturn(new ResultadoLogin("novo-access", "novo-refresh", "Maria Silva"));

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"token-antigo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("novo-access"))
                .andExpect(jsonPath("$.refreshToken").value("novo-refresh"))
                .andExpect(jsonPath("$.nome").value("Maria Silva"));
    }

    @Test
    void refreshComTokenInvalidoVira401() throws Exception {
        when(identidadeService.renovar("token-invalido"))
                .thenThrow(new BadCredentialsException("Refresh token invalido."));

        mockMvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"token-invalido\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"))
                .andExpect(jsonPath("$.mensagem").value("Refresh token invalido."));
    }

    // --- T1.1.10: recuperacao de senha ------------------------------------

    @Test
    void recuperarSenhaSempreRetorna202EDelegaAoServico() throws Exception {
        mockMvc.perform(post("/v1/auth/recuperar-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"maria@example.com\"}"))
                .andExpect(status().isAccepted());

        verify(identidadeService).solicitarRedefinicaoSenha("maria@example.com");
    }

    @Test
    void recuperarSenhaComEmailInvalidoVira422() throws Exception {
        mockMvc.perform(post("/v1/auth/recuperar-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-e-email\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"));
    }

    @Test
    void redefinirSenhaValidaRetorna204() throws Exception {
        mockMvc.perform(post("/v1/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok-bruto\",\"novaSenha\":\"nova-senha-123\"}"))
                .andExpect(status().isNoContent());

        verify(identidadeService).redefinirSenha("tok-bruto", "nova-senha-123");
    }

    @Test
    void redefinirSenhaComTokenInvalidoVira422() throws Exception {
        doThrow(new RegraDeNegocioException("TOKEN_REDEFINICAO_INVALIDO",
                "Link de redefinicao invalido, expirado ou ja utilizado."))
                .when(identidadeService).redefinirSenha(anyString(), anyString());

        mockMvc.perform(post("/v1/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok-ruim\",\"novaSenha\":\"nova-senha-123\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TOKEN_REDEFINICAO_INVALIDO"));
    }

    @Test
    void redefinirSenhaComNovaSenhaCurtaVira422() throws Exception {
        mockMvc.perform(post("/v1/auth/redefinir-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok\",\"novaSenha\":\"curta\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"))
                .andExpect(jsonPath("$.detalhes[0].campo").value("novaSenha"));
    }
}
