package br.com.unifinancas.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 403 (SAD Sec13.2): AMBIENTE_SEM_VINCULO para {@link VinculoInativoException}
 * (RN-04), PERFIL_INSUFICIENTE para qualquer outro AccessDeniedException
 * (autorizacao por perfil - Sec8.2, ainda sem tarefa que a lance).
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ErroResponseWriter erroResponseWriter;

    public JsonAccessDeniedHandler(ErroResponseWriter erroResponseWriter) {
        this.erroResponseWriter = erroResponseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        if (accessDeniedException instanceof VinculoInativoException) {
            erroResponseWriter.escrever(response, HttpServletResponse.SC_FORBIDDEN, "AMBIENTE_SEM_VINCULO",
                    accessDeniedException.getMessage());
        } else {
            erroResponseWriter.escrever(response, HttpServletResponse.SC_FORBIDDEN, "PERFIL_INSUFICIENTE",
                    "Voce nao tem permissao para executar esta acao.");
        }
    }
}
