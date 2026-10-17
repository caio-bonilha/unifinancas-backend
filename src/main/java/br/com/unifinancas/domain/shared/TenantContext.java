package br.com.unifinancas.domain.shared;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import java.util.UUID;

/**
 * Quem esta fazendo a requisicao e em qual Ambiente Financeiro (SAD Sec12.4).
 * Resolvido uma vez por requisicao pelo {@code TenantContextFilter}
 * (infrastructure/security) e passado explicitamente para quem precisar -
 * nunca lido de um estado global/ThreadLocal implicito por dominio ou
 * application (Sec12.4, item 2: "nao ha acesso implicito/global a qual e
 * o tenant atual").
 */
public record TenantContext(UUID usuarioId, UUID ambienteFinanceiroId, PerfilUsuarioAmbiente perfil) {
}
