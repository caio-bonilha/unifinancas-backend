package br.com.unifinancas.domain.notificacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificacaoTest {

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();
    private final UUID referenciaId = UUID.randomUUID();

    @Test
    void novaNotificacaoNasceNaoLidaComTodosOsCamposPreenchidos() {
        Notificacao notificacao = Notificacao.novo(ambienteFinanceiroId, usuarioId, TipoNotificacao.VENCIMENTO_CONTA,
                "Conta vencendo", "Sua conta de luz vence amanha.", "lancamento", referenciaId, CanalNotificacao.IN_APP);

        assertThat(notificacao.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(notificacao.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(notificacao.getTipo()).isEqualTo(TipoNotificacao.VENCIMENTO_CONTA);
        assertThat(notificacao.getTitulo()).isEqualTo("Conta vencendo");
        assertThat(notificacao.getMensagem()).isEqualTo("Sua conta de luz vence amanha.");
        assertThat(notificacao.getReferenciaTipo()).isEqualTo("lancamento");
        assertThat(notificacao.getReferenciaId()).isEqualTo(referenciaId);
        assertThat(notificacao.getCanal()).isEqualTo(CanalNotificacao.IN_APP);
        assertThat(notificacao.isLida()).isFalse();
        assertThat(notificacao.getEnviadaEm()).isNull();
        assertThat(notificacao.getCriadoEm()).isNotNull();
    }

    @Test
    void novaNotificacaoSemReferenciaFuncionaNormalmente() {
        Notificacao notificacao = Notificacao.novo(ambienteFinanceiroId, usuarioId, TipoNotificacao.OUTRO,
                "Aviso geral", "Mensagem sem origem especifica.", null, null, CanalNotificacao.EMAIL);

        assertThat(notificacao.getReferenciaTipo()).isNull();
        assertThat(notificacao.getReferenciaId()).isNull();
        assertThat(notificacao.getCanal()).isEqualTo(CanalNotificacao.EMAIL);
    }

    @Test
    void marcarComoLidaMudaLidaParaTrueEEIdempotente() {
        Notificacao notificacao = Notificacao.novo(ambienteFinanceiroId, usuarioId, TipoNotificacao.VENCIMENTO_CONTA,
                "Conta vencendo", "Sua conta de luz vence amanha.", "lancamento", referenciaId, CanalNotificacao.IN_APP);

        notificacao.marcarComoLida();
        assertThat(notificacao.isLida()).isTrue();

        notificacao.marcarComoLida();
        assertThat(notificacao.isLida()).isTrue();
    }

    @Test
    void marcarComoEnviadaPreencheEnviadaEm() {
        Notificacao notificacao = Notificacao.novo(ambienteFinanceiroId, usuarioId, TipoNotificacao.VENCIMENTO_CONTA,
                "Conta vencendo", "Sua conta de luz vence amanha.", "lancamento", referenciaId, CanalNotificacao.EMAIL);
        assertThat(notificacao.getEnviadaEm()).isNull();

        notificacao.marcarComoEnviada();

        assertThat(notificacao.getEnviadaEm()).isNotNull();
    }
}
