package br.com.unifinancas.presentation.dto;

/** Saida de login/refresh (SAD §7.2). {@code nome}: gap identificado ao planejar o menu do usuario no cabecalho. */
public record TokensResponse(String accessToken, String refreshToken, String nome) {
}
