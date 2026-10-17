package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import jakarta.validation.constraints.NotNull;

/**
 * SAD §7.2 - PATCH /v1/ambientes/{id}/membros/{usuarioId}. {@code perfil} e
 * obrigatorio somente quando {@code acao=ALTERAR_PERFIL} (validado no
 * controller - depende de outro campo, nao se encaixa em Bean Validation
 * simples).
 */
public record AlterarMembroRequest(
        @NotNull(message = "acao e obrigatoria") AcaoMembro acao, PerfilUsuarioAmbiente perfil) {
}
