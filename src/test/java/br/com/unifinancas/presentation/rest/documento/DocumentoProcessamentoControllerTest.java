package br.com.unifinancas.presentation.rest.documento;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.application.documento.ConfirmacaoDocumentoResultado;
import br.com.unifinancas.application.documento.DocumentoService;
import br.com.unifinancas.application.documento.ProcessamentoDocumentoDetalhe;
import br.com.unifinancas.application.documento.ProcessamentoDocumentoResumo;
import br.com.unifinancas.application.documento.ProcessamentoDocumentoService;
import br.com.unifinancas.application.lancamento.LancamentoResumo;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.documento.StatusProcessamento;
import br.com.unifinancas.domain.documento.TipoDocumento;
import br.com.unifinancas.domain.lancamento.OrigemLancamento;
import br.com.unifinancas.domain.lancamento.StatusLancamento;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.shared.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

class DocumentoProcessamentoControllerTest {

    private final DocumentoService documentoService = mock(DocumentoService.class);
    private final ProcessamentoDocumentoService processamentoDocumentoService = mock(ProcessamentoDocumentoService.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();
    private final UUID processamentoId = UUID.randomUUID();
    private final TenantContext contexto =
            new TenantContext(UUID.randomUUID(), ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver tenantResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                return TenantContext.class.equals(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                    org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                    org.springframework.web.context.request.NativeWebRequest webRequest,
                    org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return contexto;
            }
        };
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DocumentoController(documentoService, processamentoDocumentoService, objectMapper))
                .setControllerAdvice(new br.com.unifinancas.presentation.exception.GlobalExceptionHandler())
                .setCustomArgumentResolvers(tenantResolver)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void uploadSemLancamentoIdRetorna202EEnfileiraProcessamento() throws Exception {
        when(processamentoDocumentoService.registrarParaProcessamento(eq(contexto), eq(TipoDocumento.BOLETO), any(),
                eq("boleto.pdf"), any()))
                .thenReturn(new ProcessamentoDocumentoResumo(processamentoId, documentoId, TipoDocumento.BOLETO,
                        "boleto.pdf", StatusProcessamento.PENDENTE));

        mockMvc.perform(multipart("/v1/documentos")
                        .file(new MockMultipartFile("arquivo", "boleto.pdf", "application/pdf", new byte[] {1, 2}))
                        .param("tipo", "BOLETO"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documentoId").value(documentoId.toString()))
                .andExpect(jsonPath("$.processamentoDocumentoId").value(processamentoId.toString()))
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @Test
    void listaProcessamentosFiltrandoPorStatus() throws Exception {
        when(processamentoDocumentoService.listar(eq(contexto), any()))
                .thenReturn(java.util.List.of(new ProcessamentoDocumentoDetalhe(processamentoId, documentoId,
                        TipoDocumento.BOLETO, "boleto.pdf", StatusProcessamento.AGUARDANDO_REVISAO,
                        "{\"valor\":\"12.00\"}", null, 1, null)));

        mockMvc.perform(get("/v1/documentos").param("status", "AGUARDANDO_REVISAO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentoId").value(documentoId.toString()))
                .andExpect(jsonPath("$[0].status").value("AGUARDANDO_REVISAO"))
                .andExpect(jsonPath("$[0].dadosExtraidos.valor").value("12.00"));
    }

    @Test
    void getStatusRetornaDadosExtraidosComoObjetoInline() throws Exception {
        when(processamentoDocumentoService.consultarPorDocumento(contexto, documentoId))
                .thenReturn(new ProcessamentoDocumentoDetalhe(processamentoId, documentoId, TipoDocumento.BOLETO,
                        "boleto.pdf", StatusProcessamento.AGUARDANDO_REVISAO,
                        "{\"valor\":\"260.35\",\"origemExtracao\":\"PDFBOX\"}", null, 1, null));

        mockMvc.perform(get("/v1/documentos/{id}", documentoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_REVISAO"))
                .andExpect(jsonPath("$.dadosExtraidos.valor").value("260.35"))
                .andExpect(jsonPath("$.dadosExtraidos.origemExtracao").value("PDFBOX"))
                .andExpect(jsonPath("$.tentativas").value(1));
    }

    @Test
    void confirmarGeraLancamentoERetornaCorpoComLancamentoEStatusConfirmado() throws Exception {
        UUID lancamentoId = UUID.randomUUID();
        LancamentoResumo lancamento = new LancamentoResumo(lancamentoId, TipoLancamento.DESPESA,
                StatusLancamento.PENDENTE, new BigDecimal("260.35"), LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 10),
                null, null, null, null, null, OrigemLancamento.DOCUMENTO, "Conta de agua", null, null);
        when(processamentoDocumentoService.confirmarPorDocumento(eq(contexto), eq(documentoId), any()))
                .thenReturn(new ConfirmacaoDocumentoResultado(processamentoId, StatusProcessamento.CONFIRMADO, lancamento));

        String body = """
                {"tipo":"DESPESA","valor":260.35,"dataEmissao":"2026-08-25","dataVencimento":"2026-09-10",
                 "descricao":"Conta de agua"}""";

        mockMvc.perform(patch("/v1/documentos/{id}/confirmar", documentoId)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processamentoDocumentoId").value(processamentoId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.lancamento.id").value(lancamentoId.toString()))
                .andExpect(jsonPath("$.lancamento.origem").value("DOCUMENTO"));
    }

    @Test
    void deleteRetorna204EChamaOServico() throws Exception {
        mockMvc.perform(delete("/v1/documentos/{id}", documentoId))
                .andExpect(status().isNoContent());

        verify(documentoService).excluir(contexto, documentoId);
    }

    @Test
    void deleteComProcessamentoEmAndamentoRetorna422() throws Exception {
        doThrow(new br.com.unifinancas.domain.shared.RegraDeNegocioException(
                "DOCUMENTO_EM_PROCESSAMENTO", "Processamento em andamento."))
                .when(documentoService).excluir(contexto, documentoId);

        mockMvc.perform(delete("/v1/documentos/{id}", documentoId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTO_EM_PROCESSAMENTO"));
    }

    @Test
    void confirmarSemValorRetorna422() throws Exception {
        String body = """
                {"tipo":"DESPESA","dataEmissao":"2026-08-25","dataVencimento":"2026-09-10","descricao":"x"}""";

        mockMvc.perform(patch("/v1/documentos/{id}/confirmar", documentoId)
                        .contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity());
    }
}
