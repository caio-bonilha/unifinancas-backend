package br.com.unifinancas.infrastructure.persistence.jpa;

import br.com.unifinancas.application.auditoria.AcaoAuditoria;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Trilha de auditoria (SAD Sec5.2) - infraestrutura, sem entidade de dominio
 * correspondente (mesmo caso de {@link JobExecucao}). O id e gerado pelo
 * Postgres (uuidv7(), migracao V3, ADR-011).
 */
@Entity
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id")
    private UUID ambienteFinanceiroId;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false, length = 50)
    private String entidade;

    @Column(name = "entidade_id", nullable = false)
    private UUID entidadeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AcaoAuditoria acao;

    @Column(name = "dados_antes")
    @JdbcTypeCode(SqlTypes.JSON)
    private String dadosAntes;

    @Column(name = "dados_depois")
    @JdbcTypeCode(SqlTypes.JSON)
    private String dadosDepois;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Auditoria() {
        // exigido pelo JPA
    }

    public static Auditoria novo(UUID ambienteFinanceiroId, UUID usuarioId, String entidade, UUID entidadeId,
            AcaoAuditoria acao, String dadosAntes, String dadosDepois) {
        Auditoria auditoria = new Auditoria();
        auditoria.ambienteFinanceiroId = ambienteFinanceiroId;
        auditoria.usuarioId = usuarioId;
        auditoria.entidade = entidade;
        auditoria.entidadeId = entidadeId;
        auditoria.acao = acao;
        auditoria.dadosAntes = dadosAntes;
        auditoria.dadosDepois = dadosDepois;
        auditoria.criadoEm = Instant.now();
        return auditoria;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAmbienteFinanceiroId() {
        return ambienteFinanceiroId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getEntidade() {
        return entidade;
    }

    public UUID getEntidadeId() {
        return entidadeId;
    }

    public AcaoAuditoria getAcao() {
        return acao;
    }

    public String getDadosAntes() {
        return dadosAntes;
    }

    public String getDadosDepois() {
        return dadosDepois;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
