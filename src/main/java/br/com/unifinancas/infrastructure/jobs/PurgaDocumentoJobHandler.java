package br.com.unifinancas.infrastructure.jobs;

import br.com.unifinancas.application.documento.DocumentoStoragePort;
import br.com.unifinancas.domain.documento.Documento;
import br.com.unifinancas.domain.documento.DocumentoRepository;
import br.com.unifinancas.domain.documento.ProcessamentoDocumentoRepository;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * T3.1.12, ADR-020: segunda fase da exclusao de Documento. A exclusao pela UI
 * e um soft-delete ({@code documento.excluido_em}); este job diario apaga em
 * definitivo os Documentos soft-deletados ha mais tempo que a janela de
 * retencao ({@code unifinancas.documentos.purga.retencao-dias}) — remove o
 * objeto do Object Storage, o {@code ProcessamentoDocumento} 1:1 (a FK nao e
 * ON DELETE CASCADE) e a linha de {@code documento}.
 *
 * <p>Varre TODOS os Ambientes — job de sistema (SAD Sec12.6), mesmo criterio
 * de {@link RecorrenciaJobHandler}/{@link FaturaFechamentoJobHandler}. Roda na
 * transacao curta do {@link JobWorker}. Idempotente: {@code storagePort.remover}
 * ignora chave inexistente e uma reexecucao apos falha parcial reencontra os
 * mesmos registros (ainda soft-deletados).
 */
@Component
public class PurgaDocumentoJobHandler implements JobHandler {

    public static final String TIPO = "PURGA_DOCUMENTO";

    private static final Logger log = LoggerFactory.getLogger(PurgaDocumentoJobHandler.class);

    private final DocumentoRepository documentoRepository;
    private final ProcessamentoDocumentoRepository processamentoDocumentoRepository;
    private final DocumentoStoragePort documentoStoragePort;
    private final Duration retencao;

    public PurgaDocumentoJobHandler(DocumentoRepository documentoRepository,
            ProcessamentoDocumentoRepository processamentoDocumentoRepository,
            DocumentoStoragePort documentoStoragePort,
            @Value("${unifinancas.documentos.purga.retencao-dias:30}") long retencaoDias) {
        this.documentoRepository = documentoRepository;
        this.processamentoDocumentoRepository = processamentoDocumentoRepository;
        this.documentoStoragePort = documentoStoragePort;
        this.retencao = Duration.ofDays(retencaoDias);
    }

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public void executar(JobExecucao job) {
        Instant limite = Instant.now().minus(retencao);
        List<Documento> expirados = documentoRepository.findAllByExcluidoEmBefore(limite);
        if (expirados.isEmpty()) {
            return;
        }
        for (Documento documento : expirados) {
            documentoStoragePort.remover(documento.getArquivoUrl());
            processamentoDocumentoRepository.deleteByDocumentoId(documento.getId());
            documentoRepository.delete(documento);
        }
        log.info("Purga de Documento: {} arquivo(s) apagado(s) em definitivo (excluidos antes de {}).",
                expirados.size(), limite);
    }
}
