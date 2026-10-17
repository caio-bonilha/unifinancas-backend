package br.com.unifinancas.infrastructure.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Suporte tecnico ao refresh token rotativo/revogavel (SAD Sec7.4, ADR-010) -
 * infraestrutura, sem entidade de dominio correspondente (mesmo caso de
 * {@code JobExecucao}/{@code Auditoria}). Guarda so o hash do token bruto.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "familia_id", nullable = false)
    private UUID familiaId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private boolean revogado;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected RefreshToken() {
        // exigido pelo JPA
    }

    public static RefreshToken novo(UUID usuarioId, UUID familiaId, String tokenHash, Instant expiraEm) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.usuarioId = usuarioId;
        refreshToken.familiaId = familiaId;
        refreshToken.tokenHash = tokenHash;
        refreshToken.revogado = false;
        refreshToken.expiraEm = expiraEm;
        refreshToken.criadoEm = Instant.now();
        return refreshToken;
    }

    public void revogar() {
        this.revogado = true;
    }

    public boolean estaExpirado() {
        return Instant.now().isAfter(expiraEm);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getFamiliaId() {
        return familiaId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public boolean isRevogado() {
        return revogado;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
