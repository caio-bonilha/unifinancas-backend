package br.com.unifinancas.infrastructure.security;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Interceptor central de {@link RequerPerfil} (SAD Sec8.2) - evita checagem
 * de permissao espalhada e esquecida em algum caso de uso. Le o perfil do
 * {@link TenantContext} recebido como parametro pelo metodo anotado; lanca
 * {@link AccessDeniedException} (403 PERFIL_INSUFICIENTE, ja tratada pelo
 * GlobalExceptionHandler desde a T0.3.1) se o perfil nao estiver entre os
 * permitidos.
 */
@Aspect
@Component
public class RequerPerfilAspect {

    @Around("@annotation(requerPerfil)")
    public Object verificar(ProceedingJoinPoint pjp, RequerPerfil requerPerfil) throws Throwable {
        TenantContext contexto = localizarTenantContext(pjp);
        Set<PerfilUsuarioAmbiente> permitidos = Set.of(requerPerfil.value());

        if (!permitidos.contains(contexto.perfil())) {
            throw new AccessDeniedException("Perfil insuficiente para esta acao.");
        }
        return pjp.proceed();
    }

    private TenantContext localizarTenantContext(ProceedingJoinPoint pjp) {
        for (Object argumento : pjp.getArgs()) {
            if (argumento instanceof TenantContext contexto) {
                return contexto;
            }
        }
        throw new IllegalStateException(
                "@RequerPerfil exige um parametro TenantContext no metodo anotado: " + pjp.getSignature());
    }
}
