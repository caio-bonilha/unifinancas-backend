package br.com.unifinancas.application.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.application.lancamento.LancamentoResumo;
import br.com.unifinancas.application.lancamento.LancamentoService;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.documento.Documento;
import br.com.unifinancas.domain.documento.DocumentoRepository;
import br.com.unifinancas.domain.documento.ProcessamentoDocumento;
import br.com.unifinancas.domain.documento.ProcessamentoDocumentoRepository;
import br.com.unifinancas.domain.documento.StatusProcessamento;
import br.com.unifinancas.domain.documento.TipoDocumento;
import br.com.unifinancas.domain.lancamento.OrigemLancamento;
import br.com.unifinancas.domain.lancamento.StatusLancamento;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ProcessamentoDocumentoServiceTest {

    private final DocumentoRepository documentoRepository = mock(DocumentoRepository.class);
    private final ProcessamentoDocumentoRepository processamentoDocumentoRepository =
            mock(ProcessamentoDocumentoRepository.class);
    private final DocumentoStoragePort documentoStoragePort = mock(DocumentoStoragePort.class);
    private final EnfileiradorProcessamentoDocumentoPort enfileirador =
            mock(EnfileiradorProcessamentoDocumentoPort.class);
    private final LancamentoService lancamentoService = mock(LancamentoService.class);
    private final ProcessamentoDocumentoService service = new ProcessamentoDocumentoService(
            documentoRepository, processamentoDocumentoRepository, documentoStoragePort, enfileirador, lancamentoService);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);

    // --- registrarParaProcessamento -------------------------------------------------

    @Test
    void registrarSalvaDocumentoAvulsoProcessamentoPendenteEEnfileira() {
        when(documentoRepository.save(any())).thenAnswer(inv -> comId(inv.getArgument(0), UUID.randomUUID()));
        UUID processamentoId = UUID.randomUUID();
        when(processamentoDocumentoRepository.save(any())).thenAnswer(inv -> {
            ProcessamentoDocumento p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", processamentoId);
            return p;
        });

        ProcessamentoDocumentoResumo resumo = service.registrarParaProcessamento(contexto, TipoDocumento.BOLETO,
                "conteudo-pdf".getBytes(), "boleto-agua.pdf", "application/pdf");

        ArgumentCaptor<String> chave = ArgumentCaptor.forClass(String.class);
        verify(documentoStoragePort).armazenar(chave.capture(), any(), eq("application/pdf"));
        assertThat(chave.getValue()).startsWith(ambienteFinanceiroId + "/").endsWith("-boleto-agua.pdf");

        ArgumentCaptor<ProcessamentoDocumento> proc = ArgumentCaptor.forClass(ProcessamentoDocumento.class);
        verify(processamentoDocumentoRepository).save(proc.capture());
        assertThat(proc.getValue().getStatus()).isEqualTo(StatusProcessamento.PENDENTE);
        assertThat(proc.getValue().getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);

        verify(enfileirador).enfileirar(processamentoId);
        assertThat(resumo.id()).isEqualTo(processamentoId);
        assertThat(resumo.status()).isEqualTo(StatusProcessamento.PENDENTE);
        assertThat(resumo.tipo()).isEqualTo(TipoDocumento.BOLETO);
    }

    // --- consultarPorDocumento ----------------------------------------------------

    @Test
    void consultarDevolveStatusEDadosExtraidos() {
        ProcessamentoDocumento p = processamento(StatusProcessamento.AGUARDANDO_REVISAO, "{\"valor\":\"10.00\"}", null);
        when(processamentoDocumentoRepository.findByDocumentoIdAndAmbienteFinanceiroId(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(p));
        when(documentoRepository.findById(documentoId)).thenReturn(Optional.of(documento()));

        ProcessamentoDocumentoDetalhe d = service.consultarPorDocumento(contexto, documentoId);

        assertThat(d.status()).isEqualTo(StatusProcessamento.AGUARDANDO_REVISAO);
        assertThat(d.dadosExtraidosJson()).isEqualTo("{\"valor\":\"10.00\"}");
        assertThat(d.documentoId()).isEqualTo(documentoId);
        assertThat(d.tipo()).isEqualTo(TipoDocumento.BOLETO);
    }

    @Test
    void consultarDeOutroAmbienteNaoEncontra() {
        when(processamentoDocumentoRepository.findByDocumentoIdAndAmbienteFinanceiroId(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultarPorDocumento(contexto, documentoId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // --- listar -----------------------------------------------------------------

    @Test
    void listarSemFiltroUsaAConsultaGeralDoAmbiente() {
        ProcessamentoDocumento p = processamento(StatusProcessamento.AGUARDANDO_REVISAO, "{\"valor\":\"9.90\"}", null);
        when(processamentoDocumentoRepository.findAllByAmbienteFinanceiroIdOrderByIdDesc(ambienteFinanceiroId))
                .thenReturn(java.util.List.of(p));
        when(documentoRepository.findAllById(any())).thenReturn(java.util.List.of(documento()));

        var lista = service.listar(contexto, null);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).status()).isEqualTo(StatusProcessamento.AGUARDANDO_REVISAO);
        assertThat(lista.get(0).dadosExtraidosJson()).isEqualTo("{\"valor\":\"9.90\"}");
    }

    @Test
    void listarComStatusUsaAConsultaFiltrada() {
        ProcessamentoDocumento p = processamento(StatusProcessamento.ERRO, null, "falhou feio");
        when(processamentoDocumentoRepository.findAllByAmbienteFinanceiroIdAndStatusInOrderByIdDesc(
                eq(ambienteFinanceiroId), any())).thenReturn(java.util.List.of(p));
        when(documentoRepository.findAllById(any())).thenReturn(java.util.List.of(documento()));

        var lista = service.listar(contexto, java.util.List.of(StatusProcessamento.ERRO));

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).status()).isEqualTo(StatusProcessamento.ERRO);
        assertThat(lista.get(0).erroMensagem()).isEqualTo("falhou feio");
        verify(processamentoDocumentoRepository, never()).findAllByAmbienteFinanceiroIdOrderByIdDesc(any());
    }

    // --- confirmarPorDocumento --------------------------------------------------

    @Test
    void confirmarCriaLancamentoDeDocumentoEMarcaConfirmado() {
        ProcessamentoDocumento p = processamento(StatusProcessamento.AGUARDANDO_REVISAO, "{}", null);
        when(processamentoDocumentoRepository.findByDocumentoIdAndAmbienteFinanceiroId(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(p));
        Documento documento = documento();
        when(documentoRepository.findById(documentoId)).thenReturn(Optional.of(documento));
        UUID lancamentoId = UUID.randomUUID();
        when(lancamentoService.criarDeDocumento(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(resumoLancamento(lancamentoId));

        DadosRevisadosLancamento dados = new DadosRevisadosLancamento(TipoLancamento.DESPESA, new BigDecimal("260.35"),
                LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 10), null, null, null, null, "Conta de agua", null, null);

        ConfirmacaoDocumentoResultado resultado = service.confirmarPorDocumento(contexto, documentoId, dados);

        verify(lancamentoService).criarDeDocumento(eq(contexto), eq(TipoLancamento.DESPESA), eq(new BigDecimal("260.35")),
                eq(LocalDate.of(2026, 8, 25)), eq(LocalDate.of(2026, 9, 10)), any(), any(), any(), any(),
                eq("Conta de agua"), any(), any());
        // `[Revisado - Etapa B]` o documento processado fica vinculado ao Lancamento recem-criado (N:1).
        assertThat(documento.getLancamentoId()).isEqualTo(lancamentoId);
        verify(documentoRepository).save(documento);
        assertThat(p.getStatus()).isEqualTo(StatusProcessamento.CONFIRMADO);
        assertThat(p.getLancamentoGeradoId()).isEqualTo(lancamentoId);
        assertThat(resultado.status()).isEqualTo(StatusProcessamento.CONFIRMADO);
        assertThat(resultado.lancamento().id()).isEqualTo(lancamentoId);
        verify(processamentoDocumentoRepository).save(p);
    }

    @Test
    void confirmarForaDeAguardandoRevisaoEhRejeitado() {
        ProcessamentoDocumento p = processamento(StatusProcessamento.PENDENTE, null, null);
        when(processamentoDocumentoRepository.findByDocumentoIdAndAmbienteFinanceiroId(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(p));

        DadosRevisadosLancamento dados = new DadosRevisadosLancamento(TipoLancamento.DESPESA, new BigDecimal("1.00"),
                LocalDate.now(), LocalDate.now(), null, null, null, null, "x", null, null);

        assertThatThrownBy(() -> service.confirmarPorDocumento(contexto, documentoId, dados))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo())
                        .isEqualTo("PROCESSAMENTO_NAO_AGUARDA_REVISAO"));
        verify(lancamentoService, never()).criarDeDocumento(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // --- helpers -----------------------------------------------------------------

    private ProcessamentoDocumento processamento(StatusProcessamento status, String dados, String erro) {
        ProcessamentoDocumento p = ProcessamentoDocumento.novo(ambienteFinanceiroId, documentoId);
        ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
        switch (status) {
            case PENDENTE -> {
                // ja nasce PENDENTE
            }
            case PROCESSANDO -> p.iniciarProcessamento();
            case AGUARDANDO_REVISAO -> {
                p.iniciarProcessamento();
                p.registrarExtracao(dados == null ? "{}" : dados);
            }
            case ERRO -> {
                p.iniciarProcessamento();
                p.registrarErro(erro == null ? "falhou" : erro);
            }
            case CONFIRMADO -> {
                p.iniciarProcessamento();
                p.registrarExtracao(dados == null ? "{}" : dados);
                p.confirmar(UUID.randomUUID(), null);
            }
        }
        return p;
    }

    private Documento documento() {
        Documento d = Documento.novo(ambienteFinanceiroId, TipoDocumento.BOLETO, "chave/bucket.pdf", "boleto.pdf",
                100L, "application/pdf", usuarioId);
        return comId(d, documentoId);
    }

    private Documento comId(Documento d, UUID id) {
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    private LancamentoResumo resumoLancamento(UUID id) {
        return new LancamentoResumo(id, TipoLancamento.DESPESA, StatusLancamento.PENDENTE, new BigDecimal("260.35"),
                LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 10), null, null, null, null, null,
                OrigemLancamento.DOCUMENTO, "Conta de agua", null, null);
    }
}
