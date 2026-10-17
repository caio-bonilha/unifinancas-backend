package br.com.unifinancas.application.usuario;

import java.util.UUID;

/**
 * Porta para emissao de tokens (SAD Sec9.1) - application nao pode depender
 * de infrastructure.security.JwtTokenService diretamente. Implementada em
 * infrastructure (mesmo padrao de AuditoriaPort, T0.3.4).
 */
public interface EmissorTokenPort {

    TokensEmitidos emitirParDeTokens(UUID usuarioId);

    /** Valida e rotaciona um refresh token (SAD ADR-010) - lanca BadCredentialsException se invalido/expirado/reutilizado. */
    TokensEmitidos rotacionar(String refreshTokenBruto);

    /** T1.11.4, RNF-09/LGPD: revoga todos os refresh tokens do usuario - usado ao excluir a conta (RN-13), para nao deixar sessoes vivas. */
    void revogarTodosDoUsuario(UUID usuarioId);
}
