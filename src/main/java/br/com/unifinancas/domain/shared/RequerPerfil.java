package br.com.unifinancas.domain.shared;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Autorizacao declarativa por perfil (SAD Sec8.2): "anotacao declarativa por
 * caso de uso, avaliada por um aspecto/interceptor central - evita checagem
 * de permissao espalhada e esquecida em algum endpoint". Aplicada a um metodo
 * de application service que receba um {@link TenantContext} como parametro
 * (e de onde o aspecto - infrastructure/security/RequerPerfilAspect - le o
 * perfil do chamador naquele Ambiente Financeiro).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequerPerfil {

    PerfilUsuarioAmbiente[] value();
}
