package br.com.unifinancas.infrastructure.jobs;

import br.com.unifinancas.application.documento.EnfileiradorProcessamentoDocumentoPort;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucaoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementação JPA de {@link EnfileiradorProcessamentoDocumentoPort}
 * (T3.1.4): grava um {@code job_execucao} do tipo {@code PROCESSAMENTO_DOCUMENTO}
 * (ADR-005). Mesmo padrão de {@code EnfileiradorDeEnvioJpaAdapter}.
 */
@Component
public class EnfileiradorProcessamentoDocumentoJpaAdapter implements EnfileiradorProcessamentoDocumentoPort {

    private final JobExecucaoRepository jobExecucaoRepository;
    private final ObjectMapper objectMapper;

    public EnfileiradorProcessamentoDocumentoJpaAdapter(JobExecucaoRepository jobExecucaoRepository,
            ObjectMapper objectMapper) {
        this.jobExecucaoRepository = jobExecucaoRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enfileirar(UUID processamentoDocumentoId) {
        jobExecucaoRepository.save(JobExecucao.novo(ProcessamentoDocumentoOrchestrator.TIPO,
                construirPayload(processamentoDocumentoId), Instant.now()));
    }

    private String construirPayload(UUID processamentoDocumentoId) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("processamentoDocumentoId", processamentoDocumentoId.toString()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar payload do job de processamento de documento.", e);
        }
    }
}
