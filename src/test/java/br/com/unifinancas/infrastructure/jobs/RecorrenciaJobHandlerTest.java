package br.com.unifinancas.infrastructure.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.application.lancamento.LancamentoResumo;
import br.com.unifinancas.application.lancamento.LancamentoService;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.OrigemLancamento;
import br.com.unifinancas.domain.lancamento.StatusLancamento;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.lancamentorecorrente.FrequenciaRecorrencia;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrente;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrenteRepository;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RecorrenciaJobHandlerTest {

    private final LancamentoRecorrenteRepository lancamentoRecorrenteRepository = mock(LancamentoRecorrenteRepository.class);
    private final LancamentoRepository lancamentoRepository = mock(LancamentoRepository.class);
    private final LancamentoService lancamentoService = mock(LancamentoService.class);
    private final RecorrenciaJobHandler handler =
            new RecorrenciaJobHandler(lancamentoRecorrenteRepository, lancamentoRepository, lancamentoService);

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final LocalDate hoje = LocalDate.now();

    @Test
    void tipoCasaComOValorUsadoPeloJobExecucao() {
        assertThat(handler.tipo()).isEqualTo("RECORRENCIA");
    }

    @Test
    void executarMaterializaRecorrenciaQueOcorreHoje() {
        LancamentoRecorrente recorrente = recorrenciaDiariaAtivaDesde(hoje.minusDays(5));
        when(lancamentoRecorrenteRepository.findAllByAtivo(true)).thenReturn(List.of(recorrente));
        when(lancamentoRepository.existsByAmbienteFinanceiroIdAndLancamentoRecorrenteIdAndDataVencimento(
                ambienteFinanceiroId, recorrente.getId(), hoje)).thenReturn(false);
        when(lancamentoService.criarDeRecorrencia(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(resumoQualquer());

        handler.executar(JobExecucao.novo("RECORRENCIA", "{}", Instant.now()));

        verify(lancamentoService).criarDeRecorrencia(eq(ambienteFinanceiroId), eq(recorrente.getId()),
                eq(TipoLancamento.DESPESA), eq(new BigDecimal("100.00")), eq(hoje), eq(hoje), any(), any(), any(), any());
    }

    @Test
    void executarNaoMaterializaRecorrenciaQueNaoOcorreHoje() {
        LancamentoRecorrente recorrente = recorrenciaDiariaAtivaDesde(hoje.plusDays(1));
        when(lancamentoRecorrenteRepository.findAllByAtivo(true)).thenReturn(List.of(recorrente));

        handler.executar(JobExecucao.novo("RECORRENCIA", "{}", Instant.now()));

        verify(lancamentoService, never()).criarDeRecorrencia(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void executarNaoDuplicaQuandoJaFoiGeradoParaOMesmoDia() {
        LancamentoRecorrente recorrente = recorrenciaDiariaAtivaDesde(hoje.minusDays(5));
        when(lancamentoRecorrenteRepository.findAllByAtivo(true)).thenReturn(List.of(recorrente));
        when(lancamentoRepository.existsByAmbienteFinanceiroIdAndLancamentoRecorrenteIdAndDataVencimento(
                ambienteFinanceiroId, recorrente.getId(), hoje)).thenReturn(true);

        handler.executar(JobExecucao.novo("RECORRENCIA", "{}", Instant.now()));

        verify(lancamentoService, never()).criarDeRecorrencia(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private LancamentoRecorrente recorrenciaDiariaAtivaDesde(LocalDate dataInicio) {
        LancamentoRecorrente recorrente = LancamentoRecorrente.novo(ambienteFinanceiroId, "Cafe", TipoLancamento.DESPESA,
                new BigDecimal("100.00"), null, null, FrequenciaRecorrencia.DIARIA, null, dataInicio, null);
        ReflectionTestUtils.setField(recorrente, "id", UUID.randomUUID());
        return recorrente;
    }

    private LancamentoResumo resumoQualquer() {
        return new LancamentoResumo(UUID.randomUUID(), TipoLancamento.DESPESA, StatusLancamento.PENDENTE,
                new BigDecimal("100.00"), hoje, hoje, null, null, null, null, null, OrigemLancamento.RECORRENCIA, "Descrição de teste", null, null);
    }
}
