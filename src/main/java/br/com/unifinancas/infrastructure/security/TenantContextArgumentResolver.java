package br.com.unifinancas.infrastructure.security;

import br.com.unifinancas.domain.shared.TenantContext;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Permite que um controller declare {@code TenantContext contexto} como
 * parametro do metodo (SAD Sec12.4, item 2: passagem explicita, nunca
 * acesso global implicito) - o valor vem do atributo de requisicao que o
 * {@link TenantContextFilter} ja populou.
 */
@Component
public class TenantContextArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return TenantContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return webRequest.getAttribute(
                TenantContextFilter.REQUEST_ATTRIBUTE_TENANT_CONTEXT, RequestAttributes.SCOPE_REQUEST);
    }
}
