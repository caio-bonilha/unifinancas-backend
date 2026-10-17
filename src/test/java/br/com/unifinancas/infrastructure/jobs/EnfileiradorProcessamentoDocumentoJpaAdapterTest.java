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

class EnfileiradorProcessamentoDocumentoJpaAdapterTest {

    private final JobExecucaoRepository jobExecucaoRepository = mock(JobExecucaoRepository.class);
    private final EnfileiradorProcessamentoDocumentoJpaAdapter adapter =
            new EnfileiradorProcessamentoDocumentoJpaAdapter(jobExecucaoRepository, new ObjectMapper());

    @Test
    void enfileiraJobDoTipoCertoComOIdNoPayload() {
        UUID processamentoId = UUID.randomUUID();
        when(jobExecucaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adapter.enfileirar(processamentoId);

        ArgumentCaptor<JobExecucao> captor = ArgumentCaptor.forClass(JobExecucao.class);
        verify(jobExecucaoRepository).save(captor.capture());
        JobExecucao job = captor.getValue();
        assertThat(job.getTipo()).isEqualTo("PROCESSAMENTO_DOCUMENTO");
        assertThat(job.getPayload()).contains("processamentoDocumentoId").contains(processamentoId.toString());
    }
}
