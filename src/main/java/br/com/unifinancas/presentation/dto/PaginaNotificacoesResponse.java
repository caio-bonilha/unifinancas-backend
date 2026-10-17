package br.com.unifinancas.presentation.dto;

import java.util.List;

public record PaginaNotificacoesResponse(List<NotificacaoResponse> itens, String proximoCursor) {
}
