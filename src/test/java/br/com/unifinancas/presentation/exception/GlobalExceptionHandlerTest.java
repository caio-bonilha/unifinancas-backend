package br.com.unifinancas.presentation.exception;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.domain.shared.RecursoConflitanteException;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ControladorDeTeste.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.ControladorDeTeste.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void regraDeNegocioVira422ComCodigoDaRegra() throws Exception {
        mockMvc.perform(get("/teste/regra-de-negocio"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TRANSFERENCIA_MESMA_CONTA"))
                .andExpect(jsonPath("$.mensagem").value("Origem e destino nao podem ser a mesma conta."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void recursoNaoEncontradoVira404() throws Exception {
        mockMvc.perform(get("/teste/nao-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void recursoConflitanteVira409() throws Exception {
        mockMvc.perform(get("/teste/conflito"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_JA_CADASTRADO"));
    }

    @Test
    void naoAutenticadoVira401() throws Exception {
        mockMvc.perform(get("/teste/nao-autenticado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    /**
     * [Gap identificado pelo usuario]: login com credenciais erradas e mais
     * especifico que "nao autenticado" generico - mensagem amigavel em vez
     * de "Autenticacao nao realizada".
     */
    @Test
    void credenciaisInvalidasVira401ComMensagemAmigavel() throws Exception {
        mockMvc.perform(get("/teste/credenciais-invalidas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"))
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha invalidos."));
    }

    @Test
    void semPermissaoVira403() throws Exception {
        mockMvc.perform(get("/teste/sem-permissao"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PERFIL_INSUFICIENTE"));
    }

    @Test
    void validacaoDeCorpoVira422ComDetalhesPorCampo() throws Exception {
        mockMvc.perform(post("/teste/validacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"))
                .andExpect(jsonPath("$.detalhes", hasSize(1)))
                .andExpect(jsonPath("$.detalhes[0].campo").value("nome"));
    }

    @Test
    void excecaoNaoMapeadaVira500ComCodigoGenerico() throws Exception {
        mockMvc.perform(get("/teste/erro-interno"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"));
    }

    @RestController
    static class ControladorDeTeste {

        @GetMapping("/teste/regra-de-negocio")
        void regraDeNegocio() {
            throw new RegraDeNegocioException("TRANSFERENCIA_MESMA_CONTA", "Origem e destino nao podem ser a mesma conta.");
        }

        @GetMapping("/teste/nao-encontrado")
        void naoEncontrado() {
            throw new RecursoNaoEncontradoException("RECURSO_NAO_ENCONTRADO", "Lancamento nao encontrado.");
        }

        @GetMapping("/teste/conflito")
        void conflito() {
            throw new RecursoConflitanteException("EMAIL_JA_CADASTRADO", "Este e-mail ja esta cadastrado.");
        }

        @GetMapping("/teste/nao-autenticado")
        void naoAutenticado() {
            throw new InsufficientAuthenticationException("Token ausente ou invalido.");
        }

        @GetMapping("/teste/credenciais-invalidas")
        void credenciaisInvalidas() {
            throw new BadCredentialsException("E-mail ou senha invalidos.");
        }

        @GetMapping("/teste/sem-permissao")
        void semPermissao() {
            throw new AccessDeniedException("Perfil insuficiente.");
        }

        @PostMapping("/teste/validacao")
        void validacao(@Valid @RequestBody CorpoDeTeste corpo) {
        }

        @GetMapping("/teste/erro-interno")
        void erroInterno() {
            throw new RuntimeException("Falha inesperada.");
        }
    }

    record CorpoDeTeste(@NotBlank String nome) {
    }
}
