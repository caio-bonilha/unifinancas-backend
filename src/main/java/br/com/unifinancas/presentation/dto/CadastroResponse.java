package br.com.unifinancas.presentation.dto;

import java.util.UUID;

/** SAD Fluxo 6.1 - saida: tokens JWT + ambiente_financeiro_id ativo (o recem-criado) + nome do usuario. */
public record CadastroResponse(String accessToken, String refreshToken, UUID ambienteFinanceiroId, String nome) {
}
