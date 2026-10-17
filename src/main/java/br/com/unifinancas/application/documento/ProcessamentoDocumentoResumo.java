package br.com.unifinancas.application.documento;

import br.com.unifinancas.domain.documento.StatusProcessamento;
import br.com.unifinancas.domain.documento.TipoDocumento;
import java.util.UUID;

/**
 * Retorno de {@link ProcessamentoDocumentoService#registrarParaProcessamento}:
 * o processamento nasce {@code PENDENTE} e o resto (extração, revisão) é
 * assíncrono — o cliente acompanha pelo {@code id}.
 */
public record ProcessamentoDocumentoResumo(
        UUID id,
        UUID documentoId,
        TipoDocumento tipo,
        String nomeArquivo,
        StatusProcessamento status) {
}
