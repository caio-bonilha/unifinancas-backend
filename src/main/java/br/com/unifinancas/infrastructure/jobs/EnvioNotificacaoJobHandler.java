package br.com.unifinancas.infrastructure.jobs;

import br.com.unifinancas.application.notificacao.DispositivoPort;
import br.com.unifinancas.application.notificacao.EnvioNotificacaoPort;
import br.com.unifinancas.domain.notificacao.CanalNotificacao;
import br.com.unifinancas.domain.notificacao.Notificacao;
import br.com.unifinancas.domain.notificacao.NotificacaoRepository;
import br.com.unifinancas.domain.usuario.Usuario;
import br.com.unifinancas.domain.usuario.UsuarioRepository;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * T1.5.6, SAD Sec7.6: consome o job enfileirado por {@code
 * EnfileiradorDeEnvioPort} e efetivamente envia uma Notificacao PUSH ou
 * EMAIL (nunca IN_APP - essa nao passa por aqui). Uma excecao aqui propaga
 * para o {@code JobWorker} (T0.3.3), que marca o job como ERRO sem afetar
 * a linha IN_APP ja persistida nem outros jobs do lote.
 */
@Component
public class EnvioNotificacaoJobHandler implements JobHandler {

    public static final String TIPO = "ENVIO_NOTIFICACAO";

    private final NotificacaoRepository notificacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final DispositivoPort dispositivoPort;
    private final EnvioNotificacaoPort envioNotificacaoPort;
    private final ObjectMapper objectMapper;

    public EnvioNotificacaoJobHandler(NotificacaoRepository notificacaoRepository, UsuarioRepository usuarioRepository,
            DispositivoPort dispositivoPort, EnvioNotificacaoPort envioNotificacaoPort, ObjectMapper objectMapper) {
        this.notificacaoRepository = notificacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.dispositivoPort = dispositivoPort;
        this.envioNotificacaoPort = envioNotificacaoPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public void executar(JobExecucao job) {
        UUID notificacaoId = extrairNotificacaoId(job.getPayload());
        Notificacao notificacao = notificacaoRepository.findById(notificacaoId)
                .orElseThrow(() -> new IllegalStateException("Notificacao " + notificacaoId + " nao encontrada."));

        if (notificacao.getCanal() == CanalNotificacao.EMAIL) {
            enviarEmail(notificacao);
        } else if (notificacao.getCanal() == CanalNotificacao.PUSH) {
            enviarPush(notificacao);
        }

        notificacao.marcarComoEnviada();
        notificacaoRepository.save(notificacao);
    }

    private void enviarEmail(Notificacao notificacao) {
        Usuario usuario = usuarioRepository.findById(notificacao.getUsuarioId())
                .orElseThrow(() -> new IllegalStateException("Usuario " + notificacao.getUsuarioId() + " nao encontrado."));
        envioNotificacaoPort.enviarEmail(usuario.getEmail(), notificacao.getTitulo(), notificacao.getMensagem());
    }

    /** Fallback silencioso (Sec7.6): sem dispositivo registrado, nao ha erro - so nada para enviar (IN_APP ja cobre). */
    private void enviarPush(Notificacao notificacao) {
        List<String> tokens = dispositivoPort.buscarTokensDoUsuario(notificacao.getUsuarioId());
        if (tokens.isEmpty()) {
            return;
        }
        envioNotificacaoPort.enviarPush(tokens, notificacao.getTitulo(), notificacao.getMensagem());
    }

    private UUID extrairNotificacaoId(String payloadJson) {
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            return UUID.fromString(node.get("notificacaoId").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Payload invalido para job " + TIPO + ": " + payloadJson, e);
        }
    }
}
