package br.com.unifinancas.shared.config;

import br.com.unifinancas.shared.web.TraceIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra o {@link TraceIdFilter} manualmente (em vez de anota-lo com
 * {@code @Component}) para controlar a ordem explicitamente e evitar que o
 * Spring Boot o registre uma segunda vez por auto-deteccao.
 */
@Configuration
public class TraceIdFilterConfig {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
