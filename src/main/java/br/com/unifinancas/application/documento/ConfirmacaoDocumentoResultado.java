package br.com.unifinancas.application.documento;

import br.com.unifinancas.application.lancamento.LancamentoResumo;
import br.com.unifinancas.domain.documento.StatusProcessamento;
import java.util.UUID;

/**
 * Resultado de {@link ProcessamentoDocumentoService#confirmarPorDocumento}:
 * o Lançamento recém-criado (origem DOCUMENTO) e o processamento já em
 * {@code CONFIRMADO}.
 */
public record ConfirmacaoDocumentoResultado(
        UUID processamentoDocumentoId,
        StatusProcessamento status,
        LancamentoResumo lancamento) {
}
