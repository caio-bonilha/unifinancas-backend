package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.documento.StatusProcessamento;
import java.util.UUID;

/**
 * SAD §7.2, Fluxo 6.3 — resposta de {@code PATCH /v1/documentos/{id}/confirmar}:
 * o processamento fica {@code CONFIRMADO} e o Lançamento gerado é devolvido.
 */
public record ConfirmarProcessamentoResponse(
        UUID processamentoDocumentoId,
        StatusProcessamento status,
        LancamentoResponse lancamento) {
}
