package br.com.unifinancas.application.notificacao;

import java.util.List;

/** {@code proximoCursor} nulo significa que nao ha mais paginas (SAD Sec7.1, Sec17.4). */
public record PaginaNotificacoes(List<NotificacaoResumo> itens, String proximoCursor) {
}
