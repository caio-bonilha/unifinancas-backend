package br.com.unifinancas.domain.notificacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * T1.5.7 (decisao consultada com o usuario): opt-out individual do aviso
 * recorrente de vencimento proximo (Sec6.5/T1.5.4) para um Lancamento
 * especifico - escopado por (usuarioId, lancamentoId), nao pelo Ambiente
 * Financeiro inteiro, para que cada membro decida por conta propria.
 */
@Entity
@Table(name = "silencio_notificacao_vencimento")
public class SilencioNotificacaoVencimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "lancamento_id", nullable = false)
    private UUID lancamentoId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected SilencioNotificacaoVencimento() {
        // exigido pelo JPA
    }

    public static SilencioNotificacaoVencimento novo(UUID ambienteFinanceiroId, UUID usuarioId, UUID lancamentoId) {
        SilencioNotificacaoVencimento silencio = new SilencioNotificacaoVencimento();
        silencio.ambienteFinanceiroId = ambienteFinanceiroId;
        silencio.usuarioId = usuarioId;
        silencio.lancamentoId = lancamentoId;
        silencio.criadoEm = Instant.now();
        return silencio;
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

    public UUID getLancamentoId() {
        return lancamentoId;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
