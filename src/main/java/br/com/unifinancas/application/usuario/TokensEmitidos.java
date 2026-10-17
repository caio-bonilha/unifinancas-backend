package br.com.unifinancas.application.usuario;

import java.util.UUID;

/** Par access/refresh emitido para um usuario (SAD Sec7.4). */
public record TokensEmitidos(String accessToken, String refreshToken, UUID usuarioId) {
}
