package br.com.unifinancas.infrastructure.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.unifinancas.application.documento.DocumentoStoragePort;
import br.com.unifinancas.application.documento.EntradaExtracao;
import br.com.unifinancas.application.documento.OrigemExtracao;
import br.com.unifinancas.application.documento.ProcessamentoDocumentoPort;
import br.com.unifinancas.application.documento.ResultadoExtracao;
import br.com.unifinancas.application.notificacao.NotificacaoService;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiroRepository;
import br.com.unifinancas.domain.ambientefinanceiro.TipoAmbienteFinanceiro;
import br.com.unifinancas.domain.documento.Documento;
import br.com.unifinancas.domain.documento.DocumentoRepository;
import br.com.unifinancas.domain.documento.ProcessamentoDocumento;
import br.com.unifinancas.domain.documento.ProcessamentoDocumentoRepository;
import br.com.unifinancas.domain.documento.StatusProcessamento;
import br.com.unifinancas.domain.documento.TipoDocumento;
import br.com.unifinancas.domain.notificacao.TipoNotificacao;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ProcessamentoDocumentoOrchestratorTest {

    private final ProcessamentoDocumentoRepository processamentoDocumentoRepository =
            mock(ProcessamentoDocumentoRepository.class);
    private final DocumentoRepository documentoRepository = mock(DocumentoRepository.class);
    private final AmbienteFinanceiroRepository ambienteFinanceiroRepository = mock(AmbienteFinanceiroRepository.class);
    private final DocumentoStoragePort documentoStoragePort = mock(DocumentoStoragePort.class);
    private final ProcessamentoDocumentoPort processamentoDocumentoPort = mock(ProcessamentoDocumentoPort.class);
    private final NotificacaoService notificacaoService = mock(NotificacaoService.class);
    private final ProcessamentoDocumentoOrchestrator orchestrator = new ProcessamentoDocumentoOrchestrator(
            processamentoDocumentoRepository, documentoRepository, ambienteFinanceiroRepository, documentoStoragePort,
            processamentoDocumentoPort, notificacaoService, new ObjectMapper());

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();
    private final UUID processamentoId = UUID.randomUUID();
    private final UUID uploaderId = UUID.randomUUID();

    private ProcessamentoDocumento processamentoPendente() {
        ProcessamentoDocumento p = ProcessamentoDocumento.novo(ambienteFinanceiroId, documentoId);
        ReflectionTestUtils.setField(p, "id", processamentoId);
        return p;
    }

    private Documento documento() {
        Documento d = Documento.novo(ambienteFinanceiroId, TipoDocumento.BOLETO, "chave/no/bucket.pdf", "b.pdf",
                1234L, "application/pdf", uploaderId);
        ReflectionTestUtils.setField(d, "id", documentoId);
        return d;
    }

    private AmbienteFinanceiro ambiente(boolean fallbackIa) {
        AmbienteFinanceiro a = AmbienteFinanceiro.novo("Casa", TipoAmbienteFinanceiro.PESSOAL);
        if (fallbackIa) {
            a.definirOcrFallbackIaHabilitado(true);
        }
        return a;
    }

    private JobExecucao job() {
        return JobExecucao.novo(ProcessamentoDocumentoOrchestrator.TIPO,
                "{\"processamentoDocumentoId\":\"" + processamentoId + "\"}", Instant.now());
    }

    private void cenarioBasico(boolean fallbackIa) {
        when(documentoRepository.findById(documentoId)).thenReturn(Optional.of(documento()));
        when(ambienteFinanceiroRepository.findById(ambienteFinanceiroId)).thenReturn(Optional.of(ambiente(fallbackIa)));
        when(documentoStoragePort.baixar("chave/no/bucket.pdf")).thenReturn(new byte[] {1, 2, 3});
    }

    @Test
    void tipoCasaComOValorDoJobExecucao() {
        assertThat(orchestrator.tipo()).isEqualTo("PROCESSAMENTO_DOCUMENTO");
    }

    @Test
    void sucessoMoveParaAguardandoRevisaoENotificaOUploader() {
        ProcessamentoDocumento p = processamentoPendente();
        when(processamentoDocumentoRepository.findById(processamentoId)).thenReturn(Optional.of(p));
        cenarioBasico(false);
        when(processamentoDocumentoPort.extrair(any()))
                .thenReturn(ResultadoExtracao.sucesso("{\"valor\":\"10.00\"}", OrigemExtracao.PDFBOX));

        orchestrator.executar(job());

        assertThat(p.getStatus()).isEqualTo(StatusProcessamento.AGUARDANDO_REVISAO);
        assertThat(p.getDadosExtraidos()).isEqualTo("{\"valor\":\"10.00\"}");
        assertThat(p.getTentativas()).isEqualTo(1);
        verify(processamentoDocumentoRepository).save(p);
        verify(notificacaoService).criar(eq(ambienteFinanceiroId), eq(uploaderId),
                eq(TipoNotificacao.DOCUMENTO_AGUARDANDO_REVISAO), anyString(), anyString(),
                eq("processamento_documento"), eq(documentoId), any());
    }

    @Test
    void falhaDaCascataMoveParaErroENotificaFalhaProcessamento() {
        ProcessamentoDocumento p = processamentoPendente();
        when(processamentoDocumentoRepository.findById(processamentoId)).thenReturn(Optional.of(p));
        cenarioBasico(false);
        when(processamentoDocumentoPort.extrair(any()))
                .thenReturn(ResultadoExtracao.falha("Nenhuma etapa da cascata extraiu um valor"));

        orchestrator.executar(job());

        assertThat(p.getStatus()).isEqualTo(StatusProcessamento.ERRO);
        assertThat(p.getErroMensagem()).isEqualTo("Nenhuma etapa da cascata extraiu um valor");
        verify(processamentoDocumentoRepository).save(p);
        verify(notificacaoService).criar(eq(ambienteFinanceiroId), eq(uploaderId),
                eq(TipoNotificacao.FALHA_PROCESSAMENTO), anyString(), anyString(),
                eq("processamento_documento"), eq(documentoId), any());
    }

    @Test
    void erroAoBaixarDoStorageViraErroNoProcessamentoNaoExcecaoNoJob() {
        ProcessamentoDocumento p = processamentoPendente();
        when(processamentoDocumentoRepository.findById(processamentoId)).thenReturn(Optional.of(p));
        when(documentoRepository.findById(documentoId)).thenReturn(Optional.of(documento()));
        when(documentoStoragePort.baixar(any())).thenThrow(new IllegalStateException("objeto sumiu"));

        orchestrator.executar(job());

        assertThat(p.getStatus()).isEqualTo(StatusProcessamento.ERRO);
        assertThat(p.getErroMensagem()).contains("objeto sumiu");
        verify(processamentoDocumentoPort, never()).extrair(any());
        verify(notificacaoService).criar(any(), eq(uploaderId), eq(TipoNotificacao.FALHA_PROCESSAMENTO),
                anyString(), anyString(), anyString(), eq(documentoId), any());
    }

    @Test
    void documentoAusenteNaoNotifica() {
        ProcessamentoDocumento p = processamentoPendente();
        when(processamentoDocumentoRepository.findById(processamentoId)).thenReturn(Optional.of(p));
        when(documentoRepository.findById(documentoId)).thenReturn(Optional.empty());

        orchestrator.executar(job());

        assertThat(p.getStatus()).isEqualTo(StatusProcessamento.ERRO);
        verifyNoInteractions(notificacaoService);
    }

    @Test
    void jobParaProcessamentoJaEmRevisaoEIgnorado() {
        ProcessamentoDocumento p = processamentoPendente();
        p.iniciarProcessamento();
        p.registrarExtracao("{\"valor\":\"1.00\"}"); // AGUARDANDO_REVISAO
        when(processamentoDocumentoRepository.findById(processamentoId)).thenReturn(Optional.of(p));

        orchestrator.executar(job());

        assertThat(p.getStatus()).isEqualTo(StatusProcessamento.AGUARDANDO_REVISAO);
        verify(processamentoDocumentoRepository, never()).save(any());
        verifyNoInteractions(processamentoDocumentoPort, documentoStoragePort, notificacaoService);
    }

    @Test
    void retentaAPartirDeErro() {
        ProcessamentoDocumento p = processamentoPendente();
        p.iniciarProcessamento();
        p.registrarErro("timeout anterior"); // ERRO, tentativas = 1
        when(processamentoDocumentoRepository.findById(processamentoId)).thenReturn(Optional.of(p));
        cenarioBasico(false);
        when(processamentoDocumentoPort.extrair(any()))
                .thenReturn(ResultadoExtracao.sucesso("{\"valor\":\"5.00\"}", OrigemExtracao.OCR));

        orchestrator.executar(job());

        assertThat(p.getStatus()).isEqualTo(StatusProcessamento.AGUARDANDO_REVISAO);
        assertThat(p.getTentativas()).isEqualTo(2);
        assertThat(p.getErroMensagem()).isNull();
    }

    @Test
    void repassaAFlagOcrFallbackIaDoAmbienteParaACascata() {
        ProcessamentoDocumento p = processamentoPendente();
        when(processamentoDocumentoRepository.findById(processamentoId)).thenReturn(Optional.of(p));
        cenarioBasico(true);
        when(processamentoDocumentoPort.extrair(any()))
                .thenReturn(ResultadoExtracao.sucesso("{}", OrigemExtracao.IA));

        orchestrator.executar(job());

        ArgumentCaptor<EntradaExtracao> captor = ArgumentCaptor.forClass(EntradaExtracao.class);
        verify(processamentoDocumentoPort).extrair(captor.capture());
        assertThat(captor.getValue().fallbackIaHabilitado()).isTrue();
        assertThat(captor.getValue().tipo()).isEqualTo(TipoDocumento.BOLETO);
    }

    @Test
    void processamentoInexistenteFazOJobFalhar() {
        when(processamentoDocumentoRepository.findById(processamentoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orchestrator.executar(job()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(processamentoId.toString());
    }
}
