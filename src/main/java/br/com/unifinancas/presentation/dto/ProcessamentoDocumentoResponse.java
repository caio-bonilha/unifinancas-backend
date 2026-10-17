package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.documento.StatusProcessamento;
import br.com.unifinancas.domain.documento.TipoDocumento;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/**
 * SAD §7.2 — {@code GET /v1/documentos/{id}}: status do processamento e, em
 * {@code AGUARDANDO_REVISAO}, os dados extraídos pela cascata (T3.1.3) para o
 * usuário revisar. {@code dadosExtraidos} é o objeto JSON inline (ou
 * {@code null} fora de {@code AGUARDANDO_REVISAO}).
 */
public record ProcessamentoDocumentoResponse(
        UUID id,
        UUID documentoId,
        TipoDocumento tipo,
        String nomeArquivo,
        StatusProcessamento status,
        JsonNode dadosExtraidos,
        String erroMensagem,
        int tentativas,
        UUID lancamentoGeradoId) {
}
