package br.com.unifinancas.application.usuario;

import java.util.UUID;

/**
 * Saida do fluxo 6.1 (SAD): tokens + o ambiente_financeiro_id ativo (o
 * Ambiente Financeiro recem-criado). {@code nome}: mesmo gap de
 * {@link ResultadoLogin}.
 */
public record ResultadoCadastro(String accessToken, String refreshToken, UUID ambienteFinanceiroId, String nome) {
}
