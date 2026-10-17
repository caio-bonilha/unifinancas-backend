package br.com.unifinancas.presentation.dto;

import java.util.UUID;

/**
 * Corpo do {@code PATCH /v1/documentos/{id}} (Etapa B): vincula o Documento a
 * um Lancamento ou, com {@code lancamentoId} nulo/ausente, desfaz o vinculo.
 * Nunca apaga o Documento.
 */
public record VincularDocumentoRequest(UUID lancamentoId) {
}
