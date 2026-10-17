package br.com.unifinancas.presentation.rest.documento;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.application.documento.DocumentoResumo;
import br.com.unifinancas.application.documento.DocumentoService;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.documento.TipoDocumento;
import br.com.unifinancas.domain.shared.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

class DocumentoVinculoControllerTest {

    private final DocumentoService documentoService = mock(DocumentoService.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID lancamentoId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();
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
                .standaloneSetup(new DocumentoVinculoController(documentoService))
                .setCustomArgumentResolvers(tenantResolver)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private DocumentoResumo resumo(UUID lancamentoIdVinculado) {
        return new DocumentoResumo(documentoId, TipoDocumento.BOLETO, "boleto.pdf", 100L, "application/pdf",
                "https://s3.exemplo/url", Instant.parse("2026-09-01T10:00:00Z"), lancamentoIdVinculado);
    }

    @Test
    void listarDoLancamentoRetornaOsDocumentosVinculados() throws Exception {
        when(documentoService.listarDoLancamento(eq(contexto), eq(lancamentoId)))
                .thenReturn(List.of(resumo(lancamentoId)));

        mockMvc.perform(get("/v1/lancamentos/{id}/documentos", lancamentoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(documentoId.toString()))
                .andExpect(jsonPath("$[0].nomeArquivo").value("boleto.pdf"))
                .andExpect(jsonPath("$[0].lancamentoId").value(lancamentoId.toString()));
    }

    @Test
    void patchComLancamentoIdVinculaODocumento() throws Exception {
        when(documentoService.vincularLancamento(eq(contexto), eq(documentoId), eq(lancamentoId)))
                .thenReturn(resumo(lancamentoId));

        mockMvc.perform(patch("/v1/documentos/{id}", documentoId)
                        .contentType("application/json")
                        .content("{\"lancamentoId\":\"" + lancamentoId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lancamentoId").value(lancamentoId.toString()));
    }

    @Test
    void patchSemLancamentoIdDesvinculaODocumento() throws Exception {
        when(documentoService.vincularLancamento(eq(contexto), eq(documentoId), eq(null)))
                .thenReturn(resumo(null));

        mockMvc.perform(patch("/v1/documentos/{id}", documentoId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lancamentoId").doesNotExist());
    }
}
