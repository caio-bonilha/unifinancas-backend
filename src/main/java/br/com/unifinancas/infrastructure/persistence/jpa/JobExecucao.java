package br.com.unifinancas.infrastructure.persistence.jpa;

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
 * Suporte tecnico ao processamento assincrono (SAD Sec5.2, ADR-005) - nao e
 * uma entidade de dominio, e infraestrutura, por isso vive em
 * infrastructure/persistence/jpa e nao em domain. O id e gerado pelo
 * Postgres (funcao nativa {@code uuidv7()} da migracao V2, ADR-011) - o
 * Hibernate so o le de volta apos o insert (GenerationType.IDENTITY).
 */
@Entity
@Table(name = "job_execucao")
public class JobExecucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusJob status;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "agendado_para", nullable = false)
    private Instant agendadoPara;

    @Column(name = "executado_em")
    private Instant executadoEm;

    @Column(name = "erro_mensagem")
    private String erroMensagem;

    protected JobExecucao() {
        // exigido pelo JPA
    }

    public static JobExecucao novo(String tipo, String payload, Instant agendadoPara) {
        JobExecucao job = new JobExecucao();
        job.tipo = tipo;
        job.payload = payload;
        job.status = StatusJob.AGENDADO;
        job.tentativas = 0;
        job.agendadoPara = agendadoPara;
        return job;
    }

    public void marcarEmExecucao() {
        this.status = StatusJob.EM_EXECUCAO;
    }

    public void marcarConcluido() {
        this.status = StatusJob.CONCLUIDO;
        this.executadoEm = Instant.now();
    }

    public void marcarErro(String mensagem) {
        this.status = StatusJob.ERRO;
        this.tentativas = this.tentativas + 1;
        this.erroMensagem = mensagem;
        this.executadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public String getPayload() {
        return payload;
    }

    public StatusJob getStatus() {
        return status;
    }

    public int getTentativas() {
        return tentativas;
    }

    public Instant getAgendadoPara() {
        return agendadoPara;
    }

    public Instant getExecutadoEm() {
        return executadoEm;
    }

    public String getErroMensagem() {
        return erroMensagem;
    }
}
