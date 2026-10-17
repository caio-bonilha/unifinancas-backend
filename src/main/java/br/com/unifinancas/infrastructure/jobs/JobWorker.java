package br.com.unifinancas.infrastructure.jobs;

import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucaoRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Worker/agendador que consome {@code job_execucao} (SAD Sec3.4, Sec16.5,
 * ADR-005). A reivindicacao dos jobs (SELECT ... FOR UPDATE SKIP LOCKED) roda
 * em uma transacao curta e separada da execucao de cada job individual - isso
 * evita que um job lento ou com erro segure o lock/transacao dos demais do
 * mesmo lote, e permite que a falha de um job nao desfaca o progresso dos
 * outros.
 *
 * Usa {@link TransactionTemplate} (nao {@code @Transactional} nos metodos
 * internos) de proposito: com {@code @Transactional} em metodos chamados via
 * "this." dentro da mesma classe, o proxy do Spring e contornado e a
 * anotacao seria silenciosamente ignorada (auto-invocacao).
 */
@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final JobExecucaoRepository repository;
    private final Map<String, JobHandler> handlersPorTipo;
    private final TransactionTemplate transactionTemplate;
    private final int lote;

    public JobWorker(JobExecucaoRepository repository, List<JobHandler> handlers,
            PlatformTransactionManager transactionManager,
            @Value("${unifinancas.jobs.worker.lote:10}") int lote) {
        this.repository = repository;
        this.handlersPorTipo = handlers.stream().collect(Collectors.toMap(JobHandler::tipo, Function.identity()));
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.lote = lote;
    }

    @Scheduled(fixedDelayString = "${unifinancas.jobs.worker.intervalo-ms:5000}")
    public void executarProximosJobs() {
        List<UUID> reivindicados = transactionTemplate.execute(status -> {
            List<JobExecucao> jobs = repository.buscarProximosParaExecutar(lote);
            jobs.forEach(JobExecucao::marcarEmExecucao);
            return jobs.stream().map(JobExecucao::getId).toList();
        });
        for (UUID id : reivindicados) {
            executarJob(id);
        }
    }

    private void executarJob(UUID id) {
        transactionTemplate.executeWithoutResult(status -> {
            JobExecucao job = repository.findById(id).orElseThrow();
            processarJob(job);
        });
    }

    /** Logica pura de despacho, sem transacao - o que permite testar sem infraestrutura de banco. */
    void processarJob(JobExecucao job) {
        JobHandler handler = handlersPorTipo.get(job.getTipo());
        if (handler == null) {
            job.marcarErro("Nenhum handler registrado para o tipo " + job.getTipo());
            log.warn("Nenhum handler registrado para o tipo de job '{}' (job {})", job.getTipo(), job.getId());
            return;
        }
        try {
            handler.executar(job);
            job.marcarConcluido();
        } catch (Exception e) {
            job.marcarErro(e.getMessage());
            log.error("Falha ao executar job {} (tipo '{}')", job.getId(), job.getTipo(), e);
        }
    }
}
