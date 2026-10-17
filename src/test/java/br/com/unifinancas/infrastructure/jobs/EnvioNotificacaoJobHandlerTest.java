package br.com.unifinancas.infrastructure.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.application.notificacao.DispositivoPort;
import br.com.unifinancas.application.notificacao.EnvioNotificacaoPort;
import br.com.unifinancas.domain.notificacao.CanalNotificacao;
import br.com.unifinancas.domain.notificacao.Notificacao;
import br.com.unifinancas.domain.notificacao.NotificacaoRepository;
import br.com.unifinancas.domain.notificacao.TipoNotificacao;
import br.com.unifinancas.domain.usuario.Usuario;
import br.com.unifinancas.domain.usuario.UsuarioRepository;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EnvioNotificacaoJobHandlerTest {

    private final NotificacaoRepository notificacaoRepository = mock(NotificacaoRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final DispositivoPort dispositivoPort = mock(DispositivoPort.class);
    private final EnvioNotificacaoPort envioNotificacaoPort = mock(EnvioNotificacaoPort.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EnvioNotificacaoJobHandler handler = new EnvioNotificacaoJobHandler(
            notificacaoRepository, usuarioRepository, dispositivoPort, envioNotificacaoPort, objectMapper);

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();

    @Test
    void tipoCasaComOValorUsadoPeloJobExecucao() {
        assertThat(handler.tipo()).isEqualTo("ENVIO_NOTIFICACAO");
    }

    @Test
    void executarNotificacaoEmailEnviaParaOEmailDoUsuarioEMarcaComoEnviada() {
        Notificacao notificacao = Notificacao.novo(ambienteFinanceiroId, usuarioId, TipoNotificacao.VENCIMENTO_CONTA,
                "Titulo", "Mensagem", null, null, CanalNotificacao.EMAIL);
        UUID notificacaoId = UUID.randomUUID();
        ReflectionTestUtils.setField(notificacao, "id", notificacaoId);
        when(notificacaoRepository.findById(notificacaoId)).thenReturn(Optional.of(notificacao));

        Usuario usuario = Usuario.novo("Fulano", "fulano@example.com", "hash");
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        handler.executar(job(notificacaoId));

        verify(envioNotificacaoPort).enviarEmail("fulano@example.com", "Titulo", "Mensagem");
        verify(dispositivoPort, never()).buscarTokensDoUsuario(any());
        assertThat(notificacao.getEnviadaEm()).isNotNull();
        verify(notificacaoRepository).save(notificacao);
    }

    @Test
    void executarNotificacaoPushComDispositivosRegistradosEnviaParaTodosOsTokens() {
        Notificacao notificacao = Notificacao.novo(ambienteFinanceiroId, usuarioId, TipoNotificacao.VENCIMENTO_CONTA,
                "Titulo", "Mensagem", null, null, CanalNotificacao.PUSH);
        UUID notificacaoId = UUID.randomUUID();
        ReflectionTestUtils.setField(notificacao, "id", notificacaoId);
        when(notificacaoRepository.findById(notificacaoId)).thenReturn(Optional.of(notificacao));
        when(dispositivoPort.buscarTokensDoUsuario(usuarioId)).thenReturn(List.of("token-1", "token-2"));

        handler.executar(job(notificacaoId));

        verify(envioNotificacaoPort).enviarPush(List.of("token-1", "token-2"), "Titulo", "Mensagem");
        verify(usuarioRepository, never()).findById(any());
        assertThat(notificacao.getEnviadaEm()).isNotNull();
    }

    @Test
    void executarNotificacaoPushSemDispositivoRegistradoNaoEnviaMasMarcaComoEnviada() {
        Notificacao notificacao = Notificacao.novo(ambienteFinanceiroId, usuarioId, TipoNotificacao.VENCIMENTO_CONTA,
                "Titulo", "Mensagem", null, null, CanalNotificacao.PUSH);
        UUID notificacaoId = UUID.randomUUID();
        ReflectionTestUtils.setField(notificacao, "id", notificacaoId);
        when(notificacaoRepository.findById(notificacaoId)).thenReturn(Optional.of(notificacao));
        when(dispositivoPort.buscarTokensDoUsuario(usuarioId)).thenReturn(List.of());

        handler.executar(job(notificacaoId));

        verify(envioNotificacaoPort, never()).enviarPush(any(), any(), any());
        assertThat(notificacao.getEnviadaEm()).isNotNull();
    }

    @Test
    void executarComPayloadInvalidoLancaIllegalState() {
        JobExecucao invalido = JobExecucao.novo("ENVIO_NOTIFICACAO", "{}", Instant.now());

        assertThatThrownBy(() -> handler.executar(invalido)).isInstanceOf(IllegalStateException.class);
    }

    private JobExecucao job(UUID notificacaoId) {
        String payload = "{\"notificacaoId\":\"" + notificacaoId + "\"}";
        return JobExecucao.novo("ENVIO_NOTIFICACAO", payload, Instant.now());
    }
}
