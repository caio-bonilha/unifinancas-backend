package br.com.unifinancas.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Escreve o payload de erro padrao (SAD Sec7.5) diretamente na resposta,
 * para os pontos que rodam fora do MVC - entry point/access-denied handler
 * do Spring Security (Sec13, filtros da Sec8) nao passam pelo
 * {@code GlobalExceptionHandler} (presentation), que so intercepta excecao
 * de dentro de um metodo de controller. Monta o mesmo formato do
 * {@code ErroResponse} manualmente (em vez de importar a classe de
 * presentation) porque infrastructure nao pode depender de presentation
 * (SAD Sec9.1).
 */
@Component
public class ErroResponseWriter {

    private final ObjectMapper objectMapper;

    public ErroResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void escrever(HttpServletResponse response, int status, String codigo, String mensagem) throws IOException {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("codigo", codigo);
        corpo.put("mensagem", mensagem);
        corpo.put("detalhes", null);
        corpo.put("timestamp", Instant.now().toString());
        corpo.put("traceId", traceId);

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(corpo));
    }
}
