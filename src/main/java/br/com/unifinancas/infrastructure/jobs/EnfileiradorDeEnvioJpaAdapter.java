package br.com.unifinancas.infrastructure.jobs;

import br.com.unifinancas.application.notificacao.EnfileiradorDeEnvioPort;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucaoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Implementacao JPA de {@link EnfileiradorDeEnvioPort} (T1.5.6): enfileira em {@code job_execucao} (ADR-005). */
@Component
public class EnfileiradorDeEnvioJpaAdapter implements EnfileiradorDeEnvioPort {

    private final JobExecucaoRepository jobExecucaoRepository;
    private final ObjectMapper objectMapper;

    public EnfileiradorDeEnvioJpaAdapter(JobExecucaoRepository jobExecucaoRepository, ObjectMapper objectMapper) {
        this.jobExecucaoRepository = jobExecucaoRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enfileirar(UUID notificacaoId) {
        jobExecucaoRepository.save(JobExecucao.novo(EnvioNotificacaoJobHandler.TIPO, construirPayload(notificacaoId), Instant.now()));
    }

    private String construirPayload(UUID notificacaoId) {
        try {
            return objectMapper.writeValueAsString(Map.of("notificacaoId", notificacaoId.toString()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar payload do job de envio de notificacao.", e);
        }
    }
}
