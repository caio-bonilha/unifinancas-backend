package br.com.unifinancas.presentation.dto;

import java.util.List;

/** SAD §7.1/§17.4: paginacao por cursor - {@code proximoCursor} nulo significa que nao ha mais paginas. */
public record PaginaLancamentosResponse(List<LancamentoResponse> itens, String proximoCursor) {
}
