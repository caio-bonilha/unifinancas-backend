package br.com.unifinancas.application.movimentacao;

/** ADR-012, RN-05: resultado de {@code POST /v1/movimentacoes/transferencias} - as duas pernas da Transferencia. */
public record TransferenciaResumo(MovimentacaoResumo origem, MovimentacaoResumo destino) {
}
