package br.com.unifinancas.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emissao, validacao e rotacao de tokens JWT (SAD Sec7.4, Sec8.1, ADR-010).
 * Access token: JWT RS256 auto-contido (sub = usuarioId), vida curta,
 * validado so por assinatura - sem consulta ao banco no caminho quente da
 * autorizacao, que e o ponto central do ADR-010. Refresh token: string
 * opaca aleatoria (nao e JWT), cujo hash e persistido via
 * {@link RefreshTokenRepository} - e ali que mora a capacidade de
 * revogacao/rotacao que um JWT auto-contido nao teria sozinho.
 *
 * Rotacao com deteccao de reuso: cada uso de um refresh token o revoga e
 * emite outro na mesma familia (uma familia = uma sessao de login). Se um
 * token ja revogado for reapresentado, e sinal de possivel furto - a
 * familia inteira e revogada, forcando novo login em todos os dispositivos
 * daquela sessao.
 */
@Component
public class JwtTokenService {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;
    private final java.time.Duration accessTokenValidade;
    private final java.time.Duration refreshTokenValidade;
    private final RefreshTokenRepository refreshTokenRepository;

    public JwtTokenService(KeyPair keyPair, JwtProperties properties, RefreshTokenRepository refreshTokenRepository) {
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        this.keyId = properties.getKeyId();
        this.accessTokenValidade = properties.getAccessTokenValidade();
        this.refreshTokenValidade = properties.getRefreshTokenValidade();
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /** Emite o primeiro par de tokens de uma nova sessao de login (nova familia). */
    public ParDeTokens emitirParDeTokens(UUID usuarioId) {
        return emitirParDeTokens(usuarioId, UUID.randomUUID());
    }

    /**
     * Valida e rotaciona um refresh token: revoga o atual e emite um novo par
     * na mesma familia. Lanca {@link BadCredentialsException} (mapeada para
     * 401 NAO_AUTENTICADO pelo GlobalExceptionHandler) se o token for
     * desconhecido, ja revogado (e revoga a familia inteira) ou expirado.
     */
    @Transactional
    public ParDeTokens rotacionar(String refreshTokenBruto) {
        String hash = sha256(refreshTokenBruto);
        RefreshToken atual = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token invalido."));

        if (atual.isRevogado()) {
            refreshTokenRepository.revogarFamilia(atual.getFamiliaId());
            throw new BadCredentialsException("Refresh token ja utilizado - sessao revogada.");
        }
        if (atual.estaExpirado()) {
            throw new BadCredentialsException("Refresh token expirado.");
        }

        atual.revogar();
        refreshTokenRepository.save(atual);
        return emitirParDeTokens(atual.getUsuarioId(), atual.getFamiliaId());
    }

    /** Revoga todos os refresh tokens do usuario (logout de todos os dispositivos, troca de senha). */
    public void revogarTodosDoUsuario(UUID usuarioId) {
        refreshTokenRepository.revogarTodosDoUsuario(usuarioId);
    }

    /** Valida a assinatura/validade do access token e retorna o usuarioId (claim "sub"). */
    public UUID validarAccessTokenEExtrairUsuarioId(String accessToken) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(accessToken);
            return UUID.fromString(jws.getPayload().getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadCredentialsException("Access token invalido ou expirado.", e);
        }
    }

    private ParDeTokens emitirParDeTokens(UUID usuarioId, UUID familiaId) {
        String accessToken = gerarAccessToken(usuarioId);
        String refreshTokenBruto = gerarRefreshTokenBruto();

        RefreshToken registro = RefreshToken.novo(
                usuarioId, familiaId, sha256(refreshTokenBruto), Instant.now().plus(refreshTokenValidade));
        refreshTokenRepository.save(registro);

        return new ParDeTokens(accessToken, refreshTokenBruto, usuarioId);
    }

    private String gerarAccessToken(UUID usuarioId) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .header().keyId(keyId).and()
                .subject(usuarioId.toString())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(accessTokenValidade)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    private String gerarRefreshTokenBruto() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponivel na JVM.", e);
        }
    }
}
