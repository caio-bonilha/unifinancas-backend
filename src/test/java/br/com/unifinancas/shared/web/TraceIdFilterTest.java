package br.com.unifinancas.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void geraTraceIdQuandoHeaderAusente() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdDuranteRequisicao = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> traceIdDuranteRequisicao.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));

        assertThat(traceIdDuranteRequisicao.get()).isNotBlank();
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(traceIdDuranteRequisicao.get());
    }

    @Test
    void reutilizaTraceIdRecebidoNoHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-existente-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdDuranteRequisicao = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> traceIdDuranteRequisicao.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));

        assertThat(traceIdDuranteRequisicao.get()).isEqualTo("trace-existente-123");
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("trace-existente-123");
    }

    @Test
    void limpaOMdcAposARequisicao() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
    }
}
