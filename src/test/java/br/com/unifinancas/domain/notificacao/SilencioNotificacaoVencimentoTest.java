package br.com.unifinancas.domain.notificacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SilencioNotificacaoVencimentoTest {

    @Test
    void novoSilencioNasceComTodosOsCamposPreenchidos() {
        UUID ambienteFinanceiroId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID lancamentoId = UUID.randomUUID();

        SilencioNotificacaoVencimento silencio =
                SilencioNotificacaoVencimento.novo(ambienteFinanceiroId, usuarioId, lancamentoId);

        assertThat(silencio.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(silencio.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(silencio.getLancamentoId()).isEqualTo(lancamentoId);
        assertThat(silencio.getCriadoEm()).isNotNull();
    }
}
