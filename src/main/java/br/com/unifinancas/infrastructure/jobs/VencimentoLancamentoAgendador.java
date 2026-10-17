package br.com.unifinancas.infrastructure.jobs;

import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucaoRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * T1.5.4, SAD Sec6.5/ADR-005: enfileira, uma vez por dia, o job de
 * vencimento ({@link VencimentoLancamentoJobHandler}) na tabela
 * {@code job_execucao} - o {@link JobWorker} ja existente (T0.3.3) o pega
 * no proximo ciclo de polling e despacha para o handler certo. Nao chama a
 * logica de vencimento diretamente: todo processamento assincrono deste
 * sistema passa pela tabela de jobs (ADR-005), nao por um scheduler
 * paralelo bypassando essa infraestrutura.
 */
@Component
public class VencimentoLancamentoAgendador {

    private final JobExecucaoRepository jobExecucaoRepository;

    public VencimentoLancamentoAgendador(JobExecucaoRepository jobExecucaoRepository) {
        this.jobExecucaoRepository = jobExecucaoRepository;
    }

    @Scheduled(cron = "${unifinancas.jobs.vencimento.cron:0 0 5 * * *}")
    public void agendar() {
        jobExecucaoRepository.save(JobExecucao.novo(VencimentoLancamentoJobHandler.TIPO, "{}", Instant.now()));
    }
}
