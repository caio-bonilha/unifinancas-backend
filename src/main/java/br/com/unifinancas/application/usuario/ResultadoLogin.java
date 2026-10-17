package br.com.unifinancas.application.usuario;

/**
 * Saida de login/refresh (SAD RF-01, §7.2) - sem ambiente_financeiro_id (ver
 * T1.1.7). {@code nome} (gap identificado ao planejar o menu do usuario no
 * cabecalho): nada no frontend tinha o nome do usuario logado ate agora.
 */
public record ResultadoLogin(String accessToken, String refreshToken, String nome) {
}
