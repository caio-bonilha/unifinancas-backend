package br.com.unifinancas.domain.ambientefinanceiro;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
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
 * Convite de um e-mail para ingressar em um Ambiente Financeiro (RF-04, SAD
 * Sec12.3/Sec12.5). Cobre os dois casos que o SAD descreve: e-mail que ja
 * tem Usuario ({@code usuarioId} preenchido no convite) e e-mail sem conta
 * ainda ({@code usuarioId} nulo ate a ativacao criar o Usuario - T1.1.8). O
 * vinculo em {@code usuario_ambiente_financeiro} so nasce quando o convite e
 * aceito - antes disso, so este registro existe.
 */
@Entity
@Table(name = "convite")
public class Convite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PerfilUsuarioAmbiente perfil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusConvite status;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Convite() {
        // exigido pelo JPA
    }

    public static Convite novo(UUID ambienteFinanceiroId, String email, UUID usuarioId, PerfilUsuarioAmbiente perfil,
            String tokenHash, Instant expiraEm) {
        Convite convite = new Convite();
        convite.ambienteFinanceiroId = ambienteFinanceiroId;
        convite.email = email;
        convite.usuarioId = usuarioId;
        convite.perfil = perfil;
        convite.status = StatusConvite.PENDENTE;
        convite.tokenHash = tokenHash;
        convite.expiraEm = expiraEm;
        convite.criadoEm = Instant.now();
        return convite;
    }

    /** SAD Sec12.5: convite expira em 7 dias se nao aceito. */
    public boolean estaExpirado() {
        return Instant.now().isAfter(expiraEm);
    }

    public boolean estaPendente() {
        return status == StatusConvite.PENDENTE && !estaExpirado();
    }

    /** Marca o convite como aceito - lanca se ja foi aceito ou esta expirado. */
    public void aceitar() {
        if (!estaPendente()) {
            throw new RegraDeNegocioException("CONVITE_INVALIDO",
                    "Este convite nao esta mais disponivel para aceite (ja aceito, ou expirado).");
        }
        this.status = StatusConvite.ACEITO;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAmbienteFinanceiroId() {
        return ambienteFinanceiroId;
    }

    public String getEmail() {
        return email;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public PerfilUsuarioAmbiente getPerfil() {
        return perfil;
    }

    public StatusConvite getStatus() {
        return status;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
