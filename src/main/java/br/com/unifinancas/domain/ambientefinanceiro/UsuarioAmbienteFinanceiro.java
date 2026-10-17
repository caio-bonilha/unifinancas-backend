package br.com.unifinancas.domain.ambientefinanceiro;

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
 * Vincula um Usuario a um Ambiente Financeiro, carregando perfil e status do
 * vinculo (SAD Sec4.3). Referencia usuario/ambiente pelo id (uuid), nao por
 * associacao JPA (@ManyToOne) - mantem a entidade simples e evita lazy
 * loading desnecessario para algo que e sempre consultado pelos dois ids.
 */
@Entity
@Table(name = "usuario_ambiente_financeiro")
public class UsuarioAmbienteFinanceiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PerfilUsuarioAmbiente perfil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusVinculo status;

    @Column(name = "data_entrada", nullable = false)
    private Instant dataEntrada;

    protected UsuarioAmbienteFinanceiro() {
        // exigido pelo JPA
    }

    /**
     * RN-03: o primeiro vinculo de um Ambiente Financeiro e sempre OWNER, e
     * ja nasce ATIVO (nao passa por convite - RF-02).
     */
    public static UsuarioAmbienteFinanceiro primeiroVinculo(UUID usuarioId, UUID ambienteFinanceiroId) {
        return vincularComPerfil(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.OWNER);
    }

    /**
     * Cria o vinculo ja ATIVO com o perfil informado - usado ao aceitar um
     * convite (T1.1.8, RF-04): o convite em si e que fica pendente
     * ({@code Convite}), o vinculo so nasce quando aceito.
     */
    public static UsuarioAmbienteFinanceiro vincularComPerfil(UUID usuarioId, UUID ambienteFinanceiroId,
            PerfilUsuarioAmbiente perfil) {
        UsuarioAmbienteFinanceiro vinculo = new UsuarioAmbienteFinanceiro();
        vinculo.usuarioId = usuarioId;
        vinculo.ambienteFinanceiroId = ambienteFinanceiroId;
        vinculo.perfil = perfil;
        vinculo.status = StatusVinculo.ATIVO;
        vinculo.dataEntrada = Instant.now();
        return vinculo;
    }

    /** RN-04: nenhum usuario acessa dados do tenant sem vinculo ativo - usado pelo TenantContextFilter (T1.1.4). */
    public boolean estaAtivo() {
        return status == StatusVinculo.ATIVO;
    }

    /** SAD Sec12.3: altera o perfil de um membro. Quem pode chamar isso e checado na application (T1.1.8). */
    public void alterarPerfil(PerfilUsuarioAmbiente novoPerfil) {
        this.perfil = novoPerfil;
    }

    /** SAD Sec12.3: remove um membro do Ambiente Financeiro - exclusao logica, nunca fisica (Sec5.1). */
    public void remover() {
        this.status = StatusVinculo.REMOVIDO;
    }

    /**
     * Reativa um vinculo REMOVIDO (T1.1.8): quando um usuario ja teve vinculo
     * com o ambiente antes (removido) e aceita um novo convite, em vez de
     * violar a constraint unica (usuario_id, ambiente_financeiro_id) criando
     * outra linha, o vinculo existente volta a ATIVO com o perfil do convite.
     */
    public void reativar(PerfilUsuarioAmbiente perfil) {
        this.perfil = perfil;
        this.status = StatusVinculo.ATIVO;
        this.dataEntrada = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getAmbienteFinanceiroId() {
        return ambienteFinanceiroId;
    }

    public PerfilUsuarioAmbiente getPerfil() {
        return perfil;
    }

    public StatusVinculo getStatus() {
        return status;
    }

    public Instant getDataEntrada() {
        return dataEntrada;
    }
}
