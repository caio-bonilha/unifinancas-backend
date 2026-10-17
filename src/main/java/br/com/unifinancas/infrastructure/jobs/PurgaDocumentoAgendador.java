package br.com.unifinancas.infrastructure.jobs;

import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucaoRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * T3.1.12, ADR-020: enfileira, uma vez por dia, o job de purga de Documento
 * ({@link PurgaDocumentoJobHandler}) na tabela {@code job_execucao} — mesmo
 * padrao de {@link RecorrenciaAgendador}/{@link FaturaFechamentoAgendador} (so
 * enfileira; o {@link JobWorker} despacha para o handler certo).
 */
@Component
public class PurgaDocumentoAgendador {

    private final JobExecucaoRepository jobExecucaoRepository;

    public PurgaDocumentoAgendador(JobExecucaoRepository jobExecucaoRepository) {
        this.jobExecucaoRepository = jobExecucaoRepository;
    }

    @Scheduled(cron = "${unifinancas.jobs.purga-documento.cron:0 30 3 * * *}")
    public void agendar() {
        jobExecucaoRepository.save(JobExecucao.novo(PurgaDocumentoJobHandler.TIPO, "{}", Instant.now()));
    }
}
