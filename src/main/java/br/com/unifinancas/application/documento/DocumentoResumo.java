package br.com.unifinancas.application.documento;

import br.com.unifinancas.domain.documento.TipoDocumento;
import java.time.Instant;
import java.util.UUID;

public record DocumentoResumo(UUID id, TipoDocumento tipo, String nomeArquivo, long tamanhoBytes, String mimeType,
        String urlAssinada, Instant criadoEm, UUID lancamentoId) {
}
