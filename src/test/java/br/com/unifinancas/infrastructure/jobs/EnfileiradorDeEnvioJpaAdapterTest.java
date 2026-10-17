package br.com.unifinancas.infrastructure.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucaoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EnfileiradorDeEnvioJpaAdapterTest {

    private final JobExecucaoRepository jobExecucaoRepository = mock(JobExecucaoRepository.class);
    private final EnfileiradorDeEnvioJpaAdapter adapter = new EnfileiradorDeEnvioJpaAdapter(jobExecucaoRepository, new ObjectMapper());

    @Test
    void enfileirarPersisteUmJobDoTipoCertoComANotificacaoIdNoPayload() {
        UUID notificacaoId = UUID.randomUUID();
        when(jobExecucaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        adapter.enfileirar(notificacaoId);

        ArgumentCaptor<JobExecucao> captor = ArgumentCaptor.forClass(JobExecucao.class);
        verify(jobExecucaoRepository).save(captor.capture());
        JobExecucao job = captor.getValue();
        assertThat(job.getTipo()).isEqualTo("ENVIO_NOTIFICACAO");
        assertThat(job.getPayload()).contains(notificacaoId.toString());
    }
}
