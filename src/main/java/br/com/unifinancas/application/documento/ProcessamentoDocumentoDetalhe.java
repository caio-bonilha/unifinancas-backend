package br.com.unifinancas.application.documento;

import br.com.unifinancas.domain.documento.StatusProcessamento;
import br.com.unifinancas.domain.documento.TipoDocumento;
import java.util.UUID;

/**
 * Estado de um {@code ProcessamentoDocumento} para a tela de revisão (T3.1.5,
 * {@code GET /v1/documentos/{id}}). {@code dadosExtraidosJson} é o JSON cru
 * gravado pela cascata (T3.1.3) — {@code null} enquanto {@code PENDENTE}/
 * {@code PROCESSANDO} ou em {@code ERRO}.
 */
public record ProcessamentoDocumentoDetalhe(
        UUID id,
        UUID documentoId,
        TipoDocumento tipo,
        String nomeArquivo,
        StatusProcessamento status,
        String dadosExtraidosJson,
        String erroMensagem,
        int tentativas,
        UUID lancamentoGeradoId) {
}
