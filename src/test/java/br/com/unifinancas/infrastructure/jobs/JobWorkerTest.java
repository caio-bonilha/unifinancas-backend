package br.com.unifinancas.infrastructure.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucaoRepository;
import br.com.unifinancas.infrastructure.persistence.jpa.StatusJob;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class JobWorkerTest {

    private final JobExecucaoRepository repository = mock(JobExecucaoRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    @Test
    void despachaParaOHandlerDoTipoCorrespondenteEMarcaConcluido() {
        JobHandlerFake handler = new JobHandlerFake("NOTIFICACAO");
        JobWorker worker = new JobWorker(repository, List.of(handler), transactionManager, 10);
        JobExecucao job = JobExecucao.novo("NOTIFICACAO", "{}", Instant.now());

        worker.processarJob(job);

        assertThat(handler.executado).isTrue();
        assertThat(job.getStatus()).isEqualTo(StatusJob.CONCLUIDO);
        assertThat(job.getExecutadoEm()).isNotNull();
    }

    @Test
    void marcaErroEIncrementaTentativasQuandoHandlerLancaExcecao() {
        JobHandlerFake handler = new JobHandlerFake("RECORRENCIA");
        handler.lancarErro = true;
        JobWorker worker = new JobWorker(repository, List.of(handler), transactionManager, 10);
        JobExecucao job = JobExecucao.novo("RECORRENCIA", "{}", Instant.now());

        worker.processarJob(job);

        assertThat(job.getStatus()).isEqualTo(StatusJob.ERRO);
        assertThat(job.getTentativas()).isEqualTo(1);
        assertThat(job.getErroMensagem()).isEqualTo("falha proposital do teste");
    }

    @Test
    void marcaErroQuandoNaoHaHandlerRegistradoParaOTipo() {
        JobWorker worker = new JobWorker(repository, List.of(), transactionManager, 10);
        JobExecucao job = JobExecucao.novo("TIPO_SEM_HANDLER", "{}", Instant.now());

        worker.processarJob(job);

        assertThat(job.getStatus()).isEqualTo(StatusJob.ERRO);
        assertThat(job.getErroMensagem()).contains("TIPO_SEM_HANDLER");
    }

    private static class JobHandlerFake implements JobHandler {
        private final String tipo;
        private boolean lancarErro = false;
        private boolean executado = false;

        private JobHandlerFake(String tipo) {
            this.tipo = tipo;
        }

        @Override
        public String tipo() {
            return tipo;
        }

        @Override
        public void executar(JobExecucao job) {
            executado = true;
            if (lancarErro) {
                throw new RuntimeException("falha proposital do teste");
            }
        }
    }
}
