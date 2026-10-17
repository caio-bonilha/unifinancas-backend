package br.com.unifinancas.application.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import br.com.unifinancas.domain.shared.RecursoConflitanteException;
import br.com.unifinancas.domain.usuario.Usuario;
import br.com.unifinancas.domain.usuario.UsuarioRepository;
import br.com.unifinancas.infrastructure.security.JwtTokenService;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Fluxo 6.1 de ponta a ponta (SAD) contra um Postgres real (Testcontainers). */
class IdentidadeServiceIT extends AbstractIntegrationTest {

    @Autowired
    private IdentidadeService identidadeService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void registrarCriaAsTresLinhasEEmiteTokensValidos() {
        ResultadoCadastro resultado = identidadeService.registrar("Maria Silva", "maria.it@example.com", "senha12345");

        Usuario usuario = usuarioRepository.findByEmail("maria.it@example.com").orElseThrow();

        UsuarioAmbienteFinanceiro vinculo = usuarioAmbienteFinanceiroRepository
                .findByUsuarioIdAndAmbienteFinanceiroId(usuario.getId(), resultado.ambienteFinanceiroId())
                .orElseThrow();
        assertThat(vinculo.getPerfil()).isEqualTo(PerfilUsuarioAmbiente.OWNER);
        assertThat(vinculo.estaAtivo()).isTrue();

        assertThat(jwtTokenService.validarAccessTokenEExtrairUsuarioId(resultado.accessToken()))
                .isEqualTo(usuario.getId());
    }

    @Test
    void registrarComEmailJaExistenteLancaConflito() {
        identidadeService.registrar("Primeiro", "duplicado.it@example.com", "senha12345");

        assertThatThrownBy(() -> identidadeService.registrar("Segundo", "duplicado.it@example.com", "outrasenha"))
                .isInstanceOf(RecursoConflitanteException.class);
    }

    @Test
    void autenticarComACredencialCriadaNoCadastroFunciona() {
        identidadeService.registrar("Joao Silva", "joao.it@example.com", "senha12345");

        ResultadoLogin login = identidadeService.autenticar("joao.it@example.com", "senha12345");

        Usuario usuario = usuarioRepository.findByEmail("joao.it@example.com").orElseThrow();
        assertThat(jwtTokenService.validarAccessTokenEExtrairUsuarioId(login.accessToken()))
                .isEqualTo(usuario.getId());
    }

    @Test
    void autenticarComSenhaErradaLancaBadCredentials() {
        identidadeService.registrar("Ana Silva", "ana.it@example.com", "senha12345");

        assertThatThrownBy(() -> identidadeService.autenticar("ana.it@example.com", "senha-errada"))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }

    @Test
    void renovarRotacionaORefreshTokenRealDoLogin() {
        ResultadoCadastro cadastro = identidadeService.registrar("Pedro Silva", "pedro.it@example.com", "senha12345");

        ResultadoLogin renovado = identidadeService.renovar(cadastro.refreshToken());

        assertThat(renovado.refreshToken()).isNotEqualTo(cadastro.refreshToken());
        assertThatThrownBy(() -> identidadeService.renovar(cadastro.refreshToken()))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }
}
