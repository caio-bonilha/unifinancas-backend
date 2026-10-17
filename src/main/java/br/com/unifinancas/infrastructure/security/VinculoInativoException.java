package br.com.unifinancas.infrastructure.security;

import org.springframework.security.access.AccessDeniedException;

/**
 * O header X-Ambiente-Financeiro-Id esta ausente/invalido, ou o usuario nao
 * tem vinculo ativo com esse Ambiente Financeiro (RN-04, SAD Sec12.4,
 * Sec13.2 - mapeada para 403 AMBIENTE_SEM_VINCULO por
 * {@link JsonAccessDeniedHandler}). Estende a excecao do Spring Security
 * (nao {@code DomainException}) de proposito: precisa ser reconhecida pelo
 * {@code ExceptionTranslationFilter} do proprio Spring Security, que so
 * intercepta a hierarquia dele.
 */
public class VinculoInativoException extends AccessDeniedException {

    public VinculoInativoException(String mensagem) {
        super(mensagem);
    }
}
