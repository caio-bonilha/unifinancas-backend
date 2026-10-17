package br.com.unifinancas.infrastructure.security;

import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Toda rota exceto {@code /v1/auth/*} exige autenticacao (SAD Sec7.1). API
 * stateless (Sec7.4/Sec16.5): sem sessao HTTP, sem CSRF (sem cookies).
 * {@link TenantContextFilter} e inserido logo depois do
 * {@code ExceptionTranslationFilter} para que as excecoes que ele lanca
 * (token invalido, vinculo inativo) sejam capturadas e traduzidas pelos
 * handlers JSON abaixo, em vez de vazarem como erro cru do servlet.
 *
 * <p>CORS (T1.9.1/gap): o frontend web (T1.9.1, ADR-003) roda numa origem
 * diferente da API (Sec9, pacote "shared/config" ja antecipava essa
 * configuracao, mas nenhuma tarefa do roadmap a criava ate agora) - sem
 * isso, o navegador bloqueia toda chamada cross-origin antes mesmo dela
 * chegar ao backend. {@code allowCredentials(false)}: a API nunca usa
 * cookies (JWT stateless, Sec7.4), so o header {@code Authorization}, que
 * nao e afetado por essa flag.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenService jwtTokenService,
            UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository,
            JsonAuthenticationEntryPoint authenticationEntryPoint, JsonAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        TenantContextFilter tenantContextFilter =
                new TenantContextFilter(jwtTokenService, usuarioAmbienteFinanceiroRepository);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v1/auth/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(tenantContextFilter, ExceptionTranslationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${unifinancas.cors.origens-permitidas:http://localhost:5173}") String[] origensPermitidas) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(origensPermitidas));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS", "QUERY"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Ambiente-Financeiro-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/v1/**", configuration);
        return source;
    }
}
