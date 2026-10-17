package br.com.unifinancas.application.lancamento;

import java.util.List;

/** {@code proximoCursor} nulo significa que nao ha mais paginas (SAD Sec7.1, Sec17.4). */
public record PaginaLancamentos(List<LancamentoResumo> itens, String proximoCursor) {
}
