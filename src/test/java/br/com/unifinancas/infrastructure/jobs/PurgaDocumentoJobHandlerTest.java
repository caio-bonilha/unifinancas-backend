package br.com.unifinancas.infrastructure.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.unifinancas.application.documento.DocumentoStoragePort;
import br.com.unifinancas.domain.documento.Documento;
import br.com.unifinancas.domain.documento.DocumentoRepository;
import br.com.unifinancas.domain.documento.ProcessamentoDocumentoRepository;
import br.com.unifinancas.domain.documento.TipoDocumento;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PurgaDocumentoJobHandlerTest {

    private final DocumentoRepository documentoRepository = mock(DocumentoRepository.class);
    private final ProcessamentoDocumentoRepository processamentoDocumentoRepository =
            mock(ProcessamentoDocumentoRepository.class);
    private final DocumentoStoragePort documentoStoragePort = mock(DocumentoStoragePort.class);
    private final PurgaDocumentoJobHandler handler = new PurgaDocumentoJobHandler(
            documentoRepository, processamentoDocumentoRepository, documentoStoragePort, 30);

    private final UUID ambienteFinanceiroId = UUID.randomUUID();

    private Documento documentoExcluido() {
        Documento documento = Documento.novo(ambienteFinanceiroId, TipoDocumento.BOLETO, "af/chave-abc-boleto.pdf",
                "boleto.pdf", 10L, "application/pdf", UUID.randomUUID());
        ReflectionTestUtils.setField(documento, "id", UUID.randomUUID());
        documento.excluir(UUID.randomUUID());
        return documento;
    }

    @Test
    void apagaObjetoNoStorageProcessamentoELinhaDoDocumento() {
        Documento documento = documentoExcluido();
        when(documentoRepository.findAllByExcluidoEmBefore(any())).thenReturn(List.of(documento));

        handler.executar(null);

        verify(documentoStoragePort).remover("af/chave-abc-boleto.pdf");
        verify(processamentoDocumentoRepository).deleteByDocumentoId(documento.getId());
        verify(documentoRepository).delete(documento);
    }

    @Test
    void semDocumentosExpiradosNaoTocaEmNada() {
        when(documentoRepository.findAllByExcluidoEmBefore(any())).thenReturn(List.of());

        handler.executar(null);

        verifyNoInteractions(documentoStoragePort);
        verify(documentoRepository, org.mockito.Mockito.never()).delete(any());
        verify(processamentoDocumentoRepository, org.mockito.Mockito.never()).deleteByDocumentoId(any());
    }

    @Test
    void usaAJanelaDeRetencaoConfigurada() {
        when(documentoRepository.findAllByExcluidoEmBefore(any())).thenReturn(List.of());

        handler.executar(null);

        ArgumentCaptor<Instant> limite = ArgumentCaptor.forClass(Instant.class);
        verify(documentoRepository).findAllByExcluidoEmBefore(limite.capture());
        Instant esperado = Instant.now().minus(30, ChronoUnit.DAYS);
        assertThat(limite.getValue()).isBetween(esperado.minusSeconds(60), esperado.plusSeconds(60));
    }
}
