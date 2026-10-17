package br.com.unifinancas.infrastructure.security;

import br.com.unifinancas.application.usuario.EmissorTokenPort;
import br.com.unifinancas.application.usuario.TokensEmitidos;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Implementacao JWT de {@link EmissorTokenPort} (SAD Sec9.1). */
@Component
public class EmissorTokenPortJwtAdapter implements EmissorTokenPort {

    private final JwtTokenService jwtTokenService;

    public EmissorTokenPortJwtAdapter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public TokensEmitidos emitirParDeTokens(UUID usuarioId) {
        ParDeTokens par = jwtTokenService.emitirParDeTokens(usuarioId);
        return new TokensEmitidos(par.accessToken(), par.refreshToken(), par.usuarioId());
    }

    @Override
    public TokensEmitidos rotacionar(String refreshTokenBruto) {
        ParDeTokens par = jwtTokenService.rotacionar(refreshTokenBruto);
        return new TokensEmitidos(par.accessToken(), par.refreshToken(), par.usuarioId());
    }

    @Override
    public void revogarTodosDoUsuario(UUID usuarioId) {
        jwtTokenService.revogarTodosDoUsuario(usuarioId);
    }
}
