package br.com.unifinancas.infrastructure.security;

import java.util.UUID;

/** Resultado de login/refresh (SAD Sec7.4): access token curto + refresh token rotativo. */
public record ParDeTokens(String accessToken, String refreshToken, UUID usuarioId) {
}
