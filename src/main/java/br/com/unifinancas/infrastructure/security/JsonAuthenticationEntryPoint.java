package br.com.unifinancas.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 401 NAO_AUTENTICADO (SAD Sec13.2) para falhas de autenticacao na cadeia do Spring Security. */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErroResponseWriter erroResponseWriter;

    public JsonAuthenticationEntryPoint(ErroResponseWriter erroResponseWriter) {
        this.erroResponseWriter = erroResponseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        erroResponseWriter.escrever(response, HttpServletResponse.SC_UNAUTHORIZED, "NAO_AUTENTICADO",
                "Autenticacao nao realizada");
    }
}
