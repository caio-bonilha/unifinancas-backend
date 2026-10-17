package br.com.unifinancas.application.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.documento.Documento;
import br.com.unifinancas.domain.documento.DocumentoRepository;
import br.com.unifinancas.domain.documento.ProcessamentoDocumento;
import br.com.unifinancas.domain.documento.ProcessamentoDocumentoRepository;
import br.com.unifinancas.domain.documento.TipoDocumento;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;

class DocumentoServiceTest {

    private final DocumentoRepository documentoRepository = mock(DocumentoRepository.class);
    private final LancamentoRepository lancamentoRepository = mock(LancamentoRepository.class);
    private final ProcessamentoDocumentoRepository processamentoDocumentoRepository =
            mock(ProcessamentoDocumentoRepository.class);
    private final DocumentoStoragePort documentoStoragePort = mock(DocumentoStoragePort.class);
    private final DocumentoService service = new DocumentoService(documentoRepository, lancamentoRepository,
            processamentoDocumentoRepository, documentoStoragePort);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);
    private final UUID lancamentoId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();

    private LancamentoFinanceiro lancamentoQualquer() {
        return LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA, new BigDecimal("100.00"),
                LocalDate.now(), LocalDate.now(), null, null, null, null, "Descrição de teste", null);
    }

    @Test
    void anexarAoLancamentoArmazenaOArquivoEVinculaODocumentoAoLancamento() {
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamentoQualquer()));
        when(documentoRepository.save(any())).thenAnswer(this::devolverOMesmoArgumento);
        when(documentoStoragePort.gerarUrlAssinada(any())).thenReturn("https://s3.exemplo/url-assinada");
        byte[] conteudo = "conteudo do boleto".getBytes(StandardCharsets.UTF_8);

        DocumentoResumo resumo = service.anexarAoLancamento(
                contexto, lancamentoId, TipoDocumento.BOLETO, conteudo, "boleto.pdf", "application/pdf");

        verify(documentoStoragePort).armazenar(any(), eq(conteudo), eq("application/pdf"));
        assertThat(resumo.tipo()).isEqualTo(TipoDocumento.BOLETO);
        assertThat(resumo.nomeArquivo()).isEqualTo("boleto.pdf");
        assertThat(resumo.lancamentoId()).isEqualTo(lancamentoId);
        assertThat(resumo.urlAssinada()).isEqualTo("https://s3.exemplo/url-assinada");

        ArgumentCaptor<Documento> salvo = ArgumentCaptor.forClass(Documento.class);
        verify(documentoRepository).save(salvo.capture());
        assertThat(salvo.getValue().getLancamentoId()).isEqualTo(lancamentoId);
        // `[Revisado - Etapa B]` o vinculo mora no Documento, o Lancamento nao e mais tocado.
        verify(lancamentoRepository, never()).save(any());
    }

    @Test
    void anexarAoLancamentoDeOutroAmbienteLancaRecursoNaoEncontrado() {
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.anexarAoLancamento(contexto, lancamentoId, TipoDocumento.BOLETO,
                "x".getBytes(StandardCharsets.UTF_8), "x.pdf", "application/pdf"))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("LANCAMENTO_NAO_ENCONTRADO"));
    }

    @Test
    void vincularLancamentoAssociaODocumentoAUmLancamentoExistente() {
        Documento documento = documentoAvulso();
        when(documentoRepository.findByIdAndAmbienteFinanceiroIdAndExcluidoEmIsNull(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(documento));
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamentoQualquer()));
        when(documentoRepository.save(any())).thenAnswer(this::devolverOMesmoArgumento);
        when(documentoStoragePort.gerarUrlAssinada(any())).thenReturn("https://s3.exemplo/url");

        DocumentoResumo resumo = service.vincularLancamento(contexto, documentoId, lancamentoId);

        assertThat(resumo.lancamentoId()).isEqualTo(lancamentoId);
        assertThat(documento.getLancamentoId()).isEqualTo(lancamentoId);
        verify(documentoRepository).save(documento);
    }

    @Test
    void vincularLancamentoComLancamentoIdNuloDesvinculaSemApagarODocumento() {
        Documento documento = documentoAvulso();
        documento.vincularALancamento(lancamentoId);
        when(documentoRepository.findByIdAndAmbienteFinanceiroIdAndExcluidoEmIsNull(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(documento));
        when(documentoRepository.save(any())).thenAnswer(this::devolverOMesmoArgumento);
        when(documentoStoragePort.gerarUrlAssinada(any())).thenReturn("https://s3.exemplo/url");

        DocumentoResumo resumo = service.vincularLancamento(contexto, documentoId, null);

        assertThat(resumo.lancamentoId()).isNull();
        assertThat(documento.getLancamentoId()).isNull();
        verify(documentoRepository).save(documento);
    }

    @Test
    void vincularLancamentoComDocumentoDeOutroAmbienteLancaRecursoNaoEncontrado() {
        when(documentoRepository.findByIdAndAmbienteFinanceiroIdAndExcluidoEmIsNull(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.vincularLancamento(contexto, documentoId, lancamentoId))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("DOCUMENTO_NAO_ENCONTRADO"));
    }

    @Test
    void vincularLancamentoComLancamentoDeOutroAmbienteLancaRecursoNaoEncontrado() {
        when(documentoRepository.findByIdAndAmbienteFinanceiroIdAndExcluidoEmIsNull(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(documentoAvulso()));
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.vincularLancamento(contexto, documentoId, lancamentoId))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("LANCAMENTO_NAO_ENCONTRADO"));
        verify(documentoRepository, never()).save(any());
    }

    @Test
    void listarDoLancamentoDevolveOsDocumentosVinculados() {
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamentoQualquer()));
        Documento documento = documentoAvulso();
        documento.vincularALancamento(lancamentoId);
        when(documentoRepository.findAllByLancamentoIdAndAmbienteFinanceiroIdAndExcluidoEmIsNullOrderByCriadoEmDesc(
                lancamentoId, ambienteFinanceiroId))
                .thenReturn(List.of(documento));
        when(documentoStoragePort.gerarUrlAssinada(any())).thenReturn("https://s3.exemplo/url");

        List<DocumentoResumo> documentos = service.listarDoLancamento(contexto, lancamentoId);

        assertThat(documentos).hasSize(1);
        assertThat(documentos.get(0).lancamentoId()).isEqualTo(lancamentoId);
    }

    // --- ADR-020: exclusão (soft-delete) -------------------------------------

    @Test
    void excluirMarcaODocumentoComoExcluidoESoltaOVinculoComOLancamento() {
        Documento documento = documentoAvulso();
        documento.vincularALancamento(lancamentoId);
        when(documentoRepository.findByIdAndAmbienteFinanceiroIdAndExcluidoEmIsNull(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(documento));
        when(processamentoDocumentoRepository.findByDocumentoIdAndAmbienteFinanceiroId(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());

        service.excluir(contexto, documentoId);

        assertThat(documento.isExcluido()).isTrue();
        assertThat(documento.getExcluidoPorUsuarioId()).isEqualTo(usuarioId);
        assertThat(documento.getLancamentoId()).isNull();
        verify(documentoRepository).save(documento);
        // soft-delete nao toca no Object Storage — a purga assincrona e que remove o arquivo.
        verify(documentoStoragePort, never()).remover(any());
    }

    @Test
    void excluirComProcessamentoEmAndamentoEBloqueado() {
        Documento documento = documentoAvulso();
        when(documentoRepository.findByIdAndAmbienteFinanceiroIdAndExcluidoEmIsNull(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(documento));
        ProcessamentoDocumento emAndamento = ProcessamentoDocumento.novo(ambienteFinanceiroId, documentoId);
        emAndamento.iniciarProcessamento(); // PENDENTE -> PROCESSANDO
        when(processamentoDocumentoRepository.findByDocumentoIdAndAmbienteFinanceiroId(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(emAndamento));

        assertThatThrownBy(() -> service.excluir(contexto, documentoId))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DOCUMENTO_EM_PROCESSAMENTO"));

        assertThat(documento.isExcluido()).isFalse();
        verify(documentoRepository, never()).save(any());
    }

    @Test
    void excluirComProcessamentoJaEmErroEPermitido() {
        Documento documento = documentoAvulso();
        when(documentoRepository.findByIdAndAmbienteFinanceiroIdAndExcluidoEmIsNull(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(documento));
        ProcessamentoDocumento comErro = ProcessamentoDocumento.novo(ambienteFinanceiroId, documentoId);
        comErro.iniciarProcessamento();
        comErro.registrarErro("cascata falhou");
        when(processamentoDocumentoRepository.findByDocumentoIdAndAmbienteFinanceiroId(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(comErro));

        service.excluir(contexto, documentoId);

        assertThat(documento.isExcluido()).isTrue();
        verify(documentoRepository).save(documento);
    }

    @Test
    void excluirDocumentoDeOutroAmbienteLancaRecursoNaoEncontrado() {
        when(documentoRepository.findByIdAndAmbienteFinanceiroIdAndExcluidoEmIsNull(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(contexto, documentoId))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("DOCUMENTO_NAO_ENCONTRADO"));
        verify(documentoRepository, never()).save(any());
    }

    private Documento documentoAvulso() {
        return Documento.novo(ambienteFinanceiroId, TipoDocumento.BOLETO, "chave/bucket.pdf", "boleto.pdf", 10L,
                "application/pdf", usuarioId);
    }

    private Documento devolverOMesmoArgumento(InvocationOnMock invocation) {
        return invocation.getArgument(0);
    }
}
