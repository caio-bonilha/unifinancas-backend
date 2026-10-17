package br.com.unifinancas.domain.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Token de redefinicao de senha ("esqueci minha senha" - SAD Sec6.7, Sec8.1,
 * RN-14, T1.1.10). Pertence a identidade global do {@link Usuario}, nao a um
 * Ambiente Financeiro - mesma natureza do refresh token. So o hash SHA-256
 * do token bruto e guardado (Sec8.4); o valor bruto vive apenas no link
 * enviado por e-mail. E de uso unico ({@code usadoEm}) e expira em
 * {@code unifinancas.auth.redefinicao-senha.validade} (default 1h).
 */
@Entity
@Table(name = "redefinicao_senha")
public class RedefinicaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected RedefinicaoSenha() {
        // exigido pelo JPA
    }

    public static RedefinicaoSenha nova(UUID usuarioId, String tokenHash, Instant expiraEm) {
        RedefinicaoSenha registro = new RedefinicaoSenha();
        registro.usuarioId = usuarioId;
        registro.tokenHash = tokenHash;
        registro.expiraEm = expiraEm;
        registro.criadoEm = Instant.now();
        return registro;
    }

    /** Uso unico + expiracao curta: so vale enquanto nao foi consumido e nao expirou. */
    public boolean estaValido() {
        return usadoEm == null && Instant.now().isBefore(expiraEm);
    }

    /**
     * Marca o token como consumido. Idempotente de proposito - o mesmo token
     * e marcado ao ser usado e de novo quando {@code redefinirSenha} invalida
     * todos os pendentes do Usuario de uma vez (SAD Sec6.7).
     */
    public void marcarUsado() {
        if (usadoEm == null) {
            this.usadoEm = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getUsadoEm() {
        return usadoEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
