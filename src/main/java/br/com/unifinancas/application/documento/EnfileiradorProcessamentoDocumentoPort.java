package br.com.unifinancas.application.documento;

import java.util.UUID;

/**
 * Enfileira o processamento assíncrono de um {@link
 * br.com.unifinancas.domain.documento.ProcessamentoDocumento} recém-criado
 * (Fluxo 6.3, ADR-005). A implementação grava uma linha em {@code
 * job_execucao} do tipo {@code PROCESSAMENTO_DOCUMENTO}; o {@code JobWorker}
 * (T0.3.3) a entrega ao {@code ProcessamentoDocumentoOrchestrator} (T3.1.4).
 * Mesma abordagem de {@code EnfileiradorDeEnvioPort} (notificações).
 */
public interface EnfileiradorProcessamentoDocumentoPort {

    void enfileirar(UUID processamentoDocumentoId);
}
