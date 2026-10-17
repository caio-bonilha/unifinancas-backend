package br.com.unifinancas.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gera (ou herda de {@value #TRACE_ID_HEADER}) um traceId unico por
 * requisicao e o disponibiliza no MDC para todo log da requisicao
 * (SAD Sec14.3). Registrado com a maior precedencia possivel
 * (ver {@link br.com.unifinancas.shared.config.TraceIdFilterConfig}) para que
 * ate os filtros de seguranca, quando existirem, ja rodem com o traceId
 * disponivel.
 */
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String recebido = request.getHeader(TRACE_ID_HEADER);
        return (recebido != null && !recebido.isBlank()) ? recebido : UUID.randomUUID().toString();
    }
}
