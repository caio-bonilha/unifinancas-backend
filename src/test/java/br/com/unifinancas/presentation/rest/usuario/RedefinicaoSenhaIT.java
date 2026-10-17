package br.com.unifinancas.presentation.rest.usuario;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.application.usuario.RedefinicaoSenhaMailerPort;
import br.com.unifinancas.presentation.dto.CadastroResponse;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T1.1.10, SAD Sec6.7/6.8, RN-14: recuperacao ("esqueci minha senha") e
 * alteracao de senha atraves dos controllers reais (MockMvc + Postgres via
 * Testcontainers). O e-mail transacional e mockado so para capturar o link
 * (e o unico lugar onde o token bruto aparece - nunca volta numa resposta).
 */
@AutoConfigureMockMvc
class RedefinicaoSenhaIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RedefinicaoSenhaMailerPort redefinicaoSenhaMailerPort;

    @Test
    void fluxoEsqueciMinhaSenha_trocaAsenhaInvalidaOtokenEDerrubaAsSessoes() throws Exception {
        CadastroResponse cadastro = cadastrar("Carla Reset", "carla.reset@example.com", "senha-antiga-1");
        String refreshAntigo = cadastro.refreshToken();

        // 1. Solicitacao: 202 sem corpo, sempre
        mockMvc.perform(post("/v1/auth/recuperar-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"carla.reset@example.com\"}"))
                .andExpect(status().isAccepted());

        // 2. O token bruto so existe no link do e-mail
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(redefinicaoSenhaMailerPort)
                .enviarLinkRedefinicao(org.mockito.ArgumentMatchers.eq("carla.reset@example.com"),
                        org.mockito.ArgumentMatchers.eq("Carla Reset"), link.capture());
        String token = link.getValue().substring(link.getValue().indexOf("token=") + "token=".length());

        // 3. Redefinicao: 204, sem tokens
        mockMvc.perform(post("/v1/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"novaSenha\":\"senha-nova-2\"}"))
                .andExpect(status().isNoContent());

        // 4. Senha antiga nao vale mais; a nova vale
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"carla.reset@example.com\",\"senha\":\"senha-antiga-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"carla.reset@example.com\",\"senha\":\"senha-nova-2\"}"))
                .andExpect(status().isOk());

        // 5. O refresh token emitido no cadastro foi revogado (RN-14 (c))
        mockMvc.perform(post("/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshAntigo + "\"}"))
                .andExpect(status().isUnauthorized());

        // 6. O mesmo token de redefinicao nao pode ser reutilizado (uso unico)
        mockMvc.perform(post("/v1/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"novaSenha\":\"outra-senha-3\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TOKEN_REDEFINICAO_INVALIDO"));
    }

    @Test
    void recuperarSenhaParaEmailSemContaTambemRetorna202SemEnviarEmail() throws Exception {
        mockMvc.perform(post("/v1/auth/recuperar-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ninguem@example.com\"}"))
                .andExpect(status().isAccepted());

        verify(redefinicaoSenhaMailerPort, org.mockito.Mockito.never())
                .enviarLinkRedefinicao(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void alterarSenhaAutenticado_exigeSenhaAtualCorretaEDerrubaAsOutrasSessoes() throws Exception {
        CadastroResponse cadastro = cadastrar("Diego Troca", "diego.troca@example.com", "senha-atual-1");
        String bearer = "Bearer " + cadastro.accessToken();
        String refreshAntigo = cadastro.refreshToken();

        // Sem header X-Ambiente-Financeiro-Id de proposito (a senha e da identidade global)

        // 1. Senha atual errada -> 422 SENHA_ATUAL_INCORRETA
        mockMvc.perform(patch("/v1/usuarios/me/senha").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"chute-errado\",\"novaSenha\":\"senha-nova-2\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SENHA_ATUAL_INCORRETA"));

        // 2. Senha atual correta -> 204
        mockMvc.perform(patch("/v1/usuarios/me/senha").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"senha-atual-1\",\"novaSenha\":\"senha-nova-2\"}"))
                .andExpect(status().isNoContent());

        // 3. Nova senha vale; a antiga nao
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"diego.troca@example.com\",\"senha\":\"senha-nova-2\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"diego.troca@example.com\",\"senha\":\"senha-atual-1\"}"))
                .andExpect(status().isUnauthorized());

        // 4. O refresh token anterior caiu (RN-14 (c))
        mockMvc.perform(post("/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshAntigo + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void alterarSenhaSemAutenticacaoVira401() throws Exception {
        mockMvc.perform(patch("/v1/usuarios/me/senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"x\",\"novaSenha\":\"senha-nova-2\"}"))
                .andExpect(status().isUnauthorized());
    }

    private CadastroResponse cadastrar(String nome, String email, String senha) throws Exception {
        String corpo = mockMvc.perform(post("/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(corpo, CadastroResponse.class);
    }
}
