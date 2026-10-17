package br.com.unifinancas.infrastructure.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.unifinancas.shared.AbstractIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Valida, contra um Postgres real (Testcontainers, SAD Sec15.2), os dois
 * pontos que nao dá pra confirmar so com mocks: o id gerado pelo Postgres
 * (uuidv7(), ADR-011/T0.1.4) volta populado na entidade, e o
 * FOR UPDATE SKIP LOCKED (ADR-005, Sec16.5) realmente impede que duas
 * transacoes concorrentes reivindiquem o mesmo job.
 */
class JobExecucaoRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private JobExecucaoRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void idEGeradoPeloPostgresAoSalvar() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        JobExecucao salvo = tx.execute(status -> repository.saveAndFlush(
                JobExecucao.novo("NOTIFICACAO", "{}", Instant.now())));

        assertThat(salvo.getId()).isNotNull();
    }

    @Test
    void buscarProximosParaExecutarRespeitaStatusVencimentoOrdemELimite() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Instant agora = Instant.now();

        tx.executeWithoutResult(status -> {
            repository.save(JobExecucao.novo("A_FUTURO", "{}", agora.plus(1, ChronoUnit.HOURS)));
            JobExecucao concluido = JobExecucao.novo("B_CONCLUIDO", "{}", agora.minusSeconds(10));
            concluido.marcarEmExecucao();
            concluido.marcarConcluido();
            repository.save(concluido);
            repository.save(JobExecucao.novo("C_MAIS_ANTIGO", "{}", agora.minusSeconds(20)));
            repository.save(JobExecucao.novo("D_MAIS_RECENTE", "{}", agora.minusSeconds(5)));
        });

        List<JobExecucao> proximos = tx.execute(status -> repository.buscarProximosParaExecutar(10));

        assertThat(proximos).extracting(JobExecucao::getTipo)
                .containsExactly("C_MAIS_ANTIGO", "D_MAIS_RECENTE");
    }

    @Test
    void skipLockedImpedeQueDuasTransacoesReivindiquemOMesmoJob() throws InterruptedException {
        TransactionTemplate txSetup = new TransactionTemplate(transactionManager);
        txSetup.executeWithoutResult(status ->
                repository.save(JobExecucao.novo("EXCLUSIVO", "{}", Instant.now().minusSeconds(1))));

        CountDownLatch primeiraTransacaoTravouOJob = new CountDownLatch(1);
        CountDownLatch podeLiberarAPrimeiraTransacao = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var primeiraTransacao = executor.submit(() -> {
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                return tx.execute(status -> {
                    List<JobExecucao> jobs = repository.buscarProximosParaExecutar(10);
                    primeiraTransacaoTravouOJob.countDown();
                    aguardar(podeLiberarAPrimeiraTransacao);
                    return jobs.size();
                });
            });

            primeiraTransacaoTravouOJob.await(10, TimeUnit.SECONDS);

            var segundaTransacao = executor.submit(() -> {
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                return tx.execute(status -> repository.buscarProximosParaExecutar(10).size());
            });

            assertThat(segundaTransacao.get(10, TimeUnit.SECONDS)).isZero();

            podeLiberarAPrimeiraTransacao.countDown();
            assertThat(primeiraTransacao.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
