package br.com.unifinancas.infrastructure.security;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TenantContextWebConfig implements WebMvcConfigurer {

    private final TenantContextArgumentResolver tenantContextArgumentResolver;

    public TenantContextWebConfig(TenantContextArgumentResolver tenantContextArgumentResolver) {
        this.tenantContextArgumentResolver = tenantContextArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(tenantContextArgumentResolver);
    }
}
