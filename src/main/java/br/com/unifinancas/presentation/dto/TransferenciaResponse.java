package br.com.unifinancas.presentation.dto;

/** ADR-012, RN-05: as duas pernas da Transferencia recem-criada. */
public record TransferenciaResponse(MovimentacaoResponse origem, MovimentacaoResponse destino) {
}
