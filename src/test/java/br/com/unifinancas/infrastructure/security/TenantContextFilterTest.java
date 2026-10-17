package br.com.unifinancas.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import br.com.unifinancas.domain.shared.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;

class TenantContextFilterTest {

    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final UsuarioAmbienteFinanceiroRepository repository = mock(UsuarioAmbienteFinanceiroRepository.class);
    private final TenantContextFilter filter = new TenantContextFilter(jwtTokenService, repository);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();

    @BeforeEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void naoFiltraRotasDeAuth() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/login");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void filtraRotasProtegidas() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/lancamentos");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void semHeaderAuthorizationLancaBadCredentials() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, new MockFilterChain()))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void semHeaderDeAmbienteLancaVinculoInativo() {
        when(jwtTokenService.validarAccessTokenEExtrairUsuarioId("token-valido")).thenReturn(usuarioId);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, new MockFilterChain()))
                .isInstanceOf(VinculoInativoException.class);
    }

    @Test
    void semVinculoEncontradoLancaVinculoInativo() {
        when(jwtTokenService.validarAccessTokenEExtrairUsuarioId("token-valido")).thenReturn(usuarioId);
        when(repository.findByUsuarioIdAndAmbienteFinanceiroId(usuarioId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-valido");
        request.addHeader("X-Ambiente-Financeiro-Id", ambienteFinanceiroId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, new MockFilterChain()))
                .isInstanceOf(VinculoInativoException.class);
    }

    @Test
    void vinculoComStatusNaoAtivoLancaVinculoInativo() {
        UsuarioAmbienteFinanceiro vinculoRemovido =
                UsuarioAmbienteFinanceiro.vincularComPerfil(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);
        vinculoRemovido.remover();
        when(jwtTokenService.validarAccessTokenEExtrairUsuarioId("token-valido")).thenReturn(usuarioId);
        when(repository.findByUsuarioIdAndAmbienteFinanceiroId(usuarioId, ambienteFinanceiroId))
                .thenReturn(Optional.of(vinculoRemovido));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-valido");
        request.addHeader("X-Ambiente-Financeiro-Id", ambienteFinanceiroId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, new MockFilterChain()))
                .isInstanceOf(VinculoInativoException.class);
    }

    @Test
    void comTokenEVinculoAtivosPopulaTenantContextESecurityContext() throws Exception {
        UsuarioAmbienteFinanceiro vinculoAtivo =
                UsuarioAmbienteFinanceiro.primeiroVinculo(usuarioId, ambienteFinanceiroId);
        when(jwtTokenService.validarAccessTokenEExtrairUsuarioId("token-valido")).thenReturn(usuarioId);
        when(repository.findByUsuarioIdAndAmbienteFinanceiroId(usuarioId, ambienteFinanceiroId))
                .thenReturn(Optional.of(vinculoAtivo));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-valido");
        request.addHeader("X-Ambiente-Financeiro-Id", ambienteFinanceiroId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        TenantContext contexto = (TenantContext) request.getAttribute(TenantContextFilter.REQUEST_ATTRIBUTE_TENANT_CONTEXT);
        assertThat(contexto.usuarioId()).isEqualTo(usuarioId);
        assertThat(contexto.ambienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(contexto.perfil()).isEqualTo(PerfilUsuarioAmbiente.OWNER);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(usuarioId);
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
    }

    @Test
    void getAmbientesAutenticaMasNaoExigeHeaderDeAmbiente() throws Exception {
        when(jwtTokenService.validarAccessTokenEExtrairUsuarioId("token-valido")).thenReturn(usuarioId);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/ambientes");
        request.addHeader("Authorization", "Bearer token-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(TenantContextFilter.REQUEST_ATTRIBUTE_TENANT_CONTEXT)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(usuarioId);
    }

    @Test
    void getAmbientesAindaExigeAutenticacao() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/ambientes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, new MockFilterChain()))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void postAmbientesTambemNaoExigeHeaderDeAmbiente() throws Exception {
        when(jwtTokenService.validarAccessTokenEExtrairUsuarioId("token-valido")).thenReturn(usuarioId);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/ambientes");
        request.addHeader("Authorization", "Bearer token-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(TenantContextFilter.REQUEST_ATTRIBUTE_TENANT_CONTEXT)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(usuarioId);
    }
}
