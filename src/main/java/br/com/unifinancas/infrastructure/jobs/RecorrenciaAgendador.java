package br.com.unifinancas.infrastructure.jobs;

import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucaoRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * T1.6.3, SAD Sec6.6/ADR-005: enfileira, uma vez por dia, o job de
 * recorrencia ({@link RecorrenciaJobHandler}) na tabela {@code job_execucao}
 * - mesmo padrao de {@link VencimentoLancamentoAgendador} (T1.5.4).
 */
@Component
public class RecorrenciaAgendador {

    private final JobExecucaoRepository jobExecucaoRepository;

    public RecorrenciaAgendador(JobExecucaoRepository jobExecucaoRepository) {
        this.jobExecucaoRepository = jobExecucaoRepository;
    }

    @Scheduled(cron = "${unifinancas.jobs.recorrencia.cron:0 0 5 * * *}")
    public void agendar() {
        jobExecucaoRepository.save(JobExecucao.novo(RecorrenciaJobHandler.TIPO, "{}", Instant.now()));
    }
}
