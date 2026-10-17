package br.com.unifinancas.domain.notificacao;

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
 * Alerta gerado a partir de eventos do dominio (RF-14, SAD Sec4.16).
 * {@code referenciaTipo}/{@code referenciaId} sao uma referencia
 * polimorfica ao objeto de origem (ex.: um Lancamento vencendo, um
 * Orcamento perto do limite) - sem FK de banco nem relacao JPA, resolvida
 * por quem consome a notificacao (Sec5.2). Criacao (regra "canal IN_APP
 * sempre persistido") e a mutacao de {@code lida}/{@code enviadaEm} ficam
 * para as tarefas que precisarem delas (T1.5.2, T1.5.5).
 */
@Entity
@Table(name = "notificacao")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNotificacao tipo;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false)
    private String mensagem;

    @Column(name = "referencia_tipo", length = 30)
    private String referenciaTipo;

    @Column(name = "referencia_id")
    private UUID referenciaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CanalNotificacao canal;

    @Column(nullable = false)
    private boolean lida;

    @Column(name = "enviada_em")
    private Instant enviadaEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Notificacao() {
        // exigido pelo JPA
    }

    public static Notificacao novo(UUID ambienteFinanceiroId, UUID usuarioId, TipoNotificacao tipo, String titulo,
            String mensagem, String referenciaTipo, UUID referenciaId, CanalNotificacao canal) {
        Notificacao notificacao = new Notificacao();
        notificacao.ambienteFinanceiroId = ambienteFinanceiroId;
        notificacao.usuarioId = usuarioId;
        notificacao.tipo = tipo;
        notificacao.titulo = titulo;
        notificacao.mensagem = mensagem;
        notificacao.referenciaTipo = referenciaTipo;
        notificacao.referenciaId = referenciaId;
        notificacao.canal = canal;
        notificacao.lida = false;
        notificacao.criadoEm = Instant.now();
        return notificacao;
    }

    /** T1.5.5: idempotente - marcar uma Notificacao ja lida como lida de novo nao e erro. */
    public void marcarComoLida() {
        this.lida = true;
    }

    /** T1.5.6: PUSH/EMAIL efetivamente enviados pelo adapter externo (Sec7.6). IN_APP nunca chama isso - ja esta "entregue" ao existir. */
    public void marcarComoEnviada() {
        this.enviadaEm = Instant.now();
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

    public TipoNotificacao getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getReferenciaTipo() {
        return referenciaTipo;
    }

    public UUID getReferenciaId() {
        return referenciaId;
    }

    public CanalNotificacao getCanal() {
        return canal;
    }

    public boolean isLida() {
        return lida;
    }

    public Instant getEnviadaEm() {
        return enviadaEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
