package br.com.unifinancas.infrastructure.security;

import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import br.com.unifinancas.domain.shared.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolve usuario_id (do access token JWT) e ambiente_financeiro_id (do
 * header X-Ambiente-Financeiro-Id), valida o vinculo ativo em
 * usuario_ambiente_financeiro e popula um {@link TenantContext} por
 * requisicao (SAD Sec7.4, Sec12.4). Roda em toda rota exceto {@code /v1/auth/*}
 * (Sec7.1). Nao e {@code @Component}: e construido manualmente dentro de
 * {@link SecurityConfig} e inserido na cadeia de filtros do Spring Security
 * - se fosse tambem um bean autodetectavel, o Spring Boot o registraria uma
 * segunda vez como filtro de servlet generico (mesmo cuidado do
 * {@code TraceIdFilter}, T0.3.2).
 *
 * <p>Excecao deliberada: {@code GET /v1/ambientes} e {@code POST /v1/ambientes}
 * (T1.1.7, RF-03) ainda autenticam o usuario, mas NAO exigem o header de
 * ambiente - o proposito desses endpoints e justamente listar/criar
 * ambientes ANTES de (ou sem precisar) o cliente ja ter um selecionado
 * (exigir o header ali seria uma dependencia circular: precisar de um
 * ambiente para descobrir ou criar ambientes). Mesmo raciocinio para
 * {@code POST /v1/ambientes/{id}/convites/aceitar} (T1.1.8, SAD Sec12.5):
 * quem aceita um convite ainda nao tem vinculo ativo com aquele ambiente -
 * exigir o header derrubaria o proprio fluxo que o endpoint existe para
 * completar. Tambem {@code POST /v1/dispositivos} (T1.5.6): um token de
 * dispositivo pertence ao Usuario, nao a um Ambiente Financeiro especifico.
 * E {@code GET /v1/usuarios/me/exportacao}/{@code DELETE /v1/usuarios/me}
 * (T1.11.4, RNF-09/LGPD): as duas acoes atravessam TODOS os Ambientes
 * Financeiros do usuario, nao um so ja selecionado. E {@code PATCH
 * /v1/usuarios/me/senha} (T1.1.10): a senha pertence a identidade global do
 * Usuario, nao a um Ambiente Financeiro.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_ATTRIBUTE_TENANT_CONTEXT = "tenantContext";

    private static final String HEADER_AUTORIZACAO = "Authorization";
    private static final String PREFIXO_BEARER = "Bearer ";
    private static final String HEADER_AMBIENTE = "X-Ambiente-Financeiro-Id";
    private static final String ROTA_AMBIENTES = "/v1/ambientes";
    private static final Pattern ROTA_ACEITAR_CONVITE = Pattern.compile("^/v1/ambientes/[^/]+/convites/aceitar$");
    private static final String ROTA_DISPOSITIVOS = "/v1/dispositivos";
    private static final String ROTA_EXPORTACAO_USUARIO = "/v1/usuarios/me/exportacao";
    private static final String ROTA_USUARIO_ME = "/v1/usuarios/me";
    private static final String ROTA_SENHA_USUARIO = "/v1/usuarios/me/senha";

    private final JwtTokenService jwtTokenService;
    private final UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository;

    public TenantContextFilter(JwtTokenService jwtTokenService,
            UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository) {
        this.jwtTokenService = jwtTokenService;
        this.usuarioAmbienteFinanceiroRepository = usuarioAmbienteFinanceiroRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UUID usuarioId = autenticar(request);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(usuarioId, null, List.of()));

        if (!exigeAmbienteAtivo(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        UsuarioAmbienteFinanceiro vinculo = resolverVinculoAtivo(request, usuarioId);
        TenantContext contexto = new TenantContext(usuarioId, vinculo.getAmbienteFinanceiroId(), vinculo.getPerfil());
        request.setAttribute(REQUEST_ATTRIBUTE_TENANT_CONTEXT, contexto);

        filterChain.doFilter(request, response);
    }

    private boolean exigeAmbienteAtivo(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String metodo = request.getMethod();

        if (ROTA_AMBIENTES.equals(uri)) {
            return !("GET".equalsIgnoreCase(metodo) || "POST".equalsIgnoreCase(metodo));
        }
        if ("POST".equalsIgnoreCase(metodo) && ROTA_ACEITAR_CONVITE.matcher(uri).matches()) {
            return false;
        }
        if ("POST".equalsIgnoreCase(metodo) && ROTA_DISPOSITIVOS.equals(uri)) {
            return false;
        }
        if ("GET".equalsIgnoreCase(metodo) && ROTA_EXPORTACAO_USUARIO.equals(uri)) {
            return false;
        }
        if ("DELETE".equalsIgnoreCase(metodo) && ROTA_USUARIO_ME.equals(uri)) {
            return false;
        }
        if ("PATCH".equalsIgnoreCase(metodo) && ROTA_SENHA_USUARIO.equals(uri)) {
            return false;
        }
        return true;
    }

    private UUID autenticar(HttpServletRequest request) {
        String header = request.getHeader(HEADER_AUTORIZACAO);
        if (header == null || !header.startsWith(PREFIXO_BEARER)) {
            throw new BadCredentialsException("Header Authorization ausente ou invalido.");
        }
        String token = header.substring(PREFIXO_BEARER.length());
        return jwtTokenService.validarAccessTokenEExtrairUsuarioId(token);
    }

    private UsuarioAmbienteFinanceiro resolverVinculoAtivo(HttpServletRequest request, UUID usuarioId) {
        UUID ambienteFinanceiroId = parsearAmbienteId(request.getHeader(HEADER_AMBIENTE));

        UsuarioAmbienteFinanceiro vinculo = usuarioAmbienteFinanceiroRepository
                .findByUsuarioIdAndAmbienteFinanceiroId(usuarioId, ambienteFinanceiroId)
                .orElseThrow(() -> new VinculoInativoException(
                        "Nenhum vinculo encontrado para este Ambiente Financeiro."));

        if (!vinculo.estaAtivo()) {
            throw new VinculoInativoException("Vinculo com o Ambiente Financeiro nao esta ativo.");
        }
        return vinculo;
    }

    private UUID parsearAmbienteId(String ambienteHeader) {
        if (ambienteHeader == null || ambienteHeader.isBlank()) {
            throw new VinculoInativoException("Header X-Ambiente-Financeiro-Id obrigatorio.");
        }
        try {
            return UUID.fromString(ambienteHeader);
        } catch (IllegalArgumentException e) {
            throw new VinculoInativoException("Header X-Ambiente-Financeiro-Id invalido.");
        }
    }
}
