package br.com.unifinancas.application.movimentacao;

import java.util.List;

/** {@code proximoCursor} nulo significa que nao ha mais paginas (SAD Sec7.1, Sec17.4, mesmo padrao de PaginaLancamentos). */
public record PaginaMovimentacoes(List<MovimentacaoResumo> itens, String proximoCursor) {
}
