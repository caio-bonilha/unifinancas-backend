package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.documento.StatusProcessamento;
import br.com.unifinancas.domain.documento.TipoDocumento;
import java.util.UUID;

/**
 * SAD §7.2, Fluxo 6.3 — resposta de {@code POST /v1/documentos} <b>sem</b>
 * {@code lancamentoId} (upload para processamento assistido). O processamento
 * nasce {@code PENDENTE} e segue assíncrono; o cliente acompanha via
 * {@code GET /v1/documentos/{documentoId}}.
 */
public record DocumentoParaProcessamentoResponse(
        UUID documentoId,
        UUID processamentoDocumentoId,
        TipoDocumento tipo,
        String nomeArquivo,
        StatusProcessamento status) {
}
