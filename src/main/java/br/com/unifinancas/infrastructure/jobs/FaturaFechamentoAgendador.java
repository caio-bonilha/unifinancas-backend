package br.com.unifinancas.infrastructure.jobs;

import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucaoRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * T2.1.2, SAD Sec6.5/ADR-005: enfileira, uma vez por dia, o job de
 * fechamento de Fatura ({@link FaturaFechamentoJobHandler}) - mesmo padrao
 * de {@code VencimentoLancamentoAgendador} (so enfileira, o {@link
 * JobWorker} ja existente despacha para o handler certo).
 */
@Component
public class FaturaFechamentoAgendador {

    private final JobExecucaoRepository jobExecucaoRepository;

    public FaturaFechamentoAgendador(JobExecucaoRepository jobExecucaoRepository) {
        this.jobExecucaoRepository = jobExecucaoRepository;
    }

    @Scheduled(cron = "${unifinancas.jobs.fatura.cron:0 30 5 * * *}")
    public void agendar() {
        jobExecucaoRepository.save(JobExecucao.novo(FaturaFechamentoJobHandler.TIPO, "{}", Instant.now()));
    }
}
