package br.com.unifinancas.application.ambientefinanceiro;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.StatusConvite;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code token} e o valor bruto (nao o hash) do link de convite/ativacao -
 * so existe neste retorno, uma unica vez (T1.1.8). Sem um NotificacaoPort de
 * e-mail ainda (Etapa 1.5), quem chama o endpoint e responsavel por entregar
 * o link ao convidado; reavaliar quando o adapter de e-mail existir.
 */
public record ConviteResumo(
        UUID id, String email, PerfilUsuarioAmbiente perfil, StatusConvite status, Instant expiraEm, String token) {
}
