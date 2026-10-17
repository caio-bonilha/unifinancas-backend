package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.StatusConvite;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code token} e o valor bruto do link de convite/ativacao, retornado uma
 * unica vez - nao ha ainda um adapter de e-mail (Etapa 1.5) que o entregue
 * automaticamente ao convidado.
 */
public record ConviteResponse(
        UUID id, String email, PerfilUsuarioAmbiente perfil, StatusConvite status, Instant expiraEm, String token) {
}
