package br.com.unifinancas.infrastructure.persistence.jpa;

import br.com.unifinancas.application.notificacao.PlataformaDispositivo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Token de dispositivo para envio de PUSH via Firebase Cloud Messaging
 * (T1.5.6) - infraestrutura, sem entidade de dominio correspondente (mesmo
 * caso de {@link RefreshToken}): pertence ao Usuario (identidade global),
 * nao a um Ambiente Financeiro.
 */
@Entity
@Table(name = "dispositivo_usuario")
public class DispositivoUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PlataformaDispositivo plataforma;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected DispositivoUsuario() {
        // exigido pelo JPA
    }

    public static DispositivoUsuario novo(UUID usuarioId, String token, PlataformaDispositivo plataforma) {
        DispositivoUsuario dispositivo = new DispositivoUsuario();
        dispositivo.usuarioId = usuarioId;
        dispositivo.token = token;
        dispositivo.plataforma = plataforma;
        dispositivo.criadoEm = Instant.now();
        dispositivo.atualizadoEm = Instant.now();
        return dispositivo;
    }

    /** Reinstalacao do app ou troca de conta no mesmo aparelho - o mesmo token passa a apontar para outro usuario. */
    public void reatribuir(UUID usuarioId, PlataformaDispositivo plataforma) {
        this.usuarioId = usuarioId;
        this.plataforma = plataforma;
        this.atualizadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getToken() {
        return token;
    }

    public PlataformaDispositivo getPlataforma() {
        return plataforma;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
