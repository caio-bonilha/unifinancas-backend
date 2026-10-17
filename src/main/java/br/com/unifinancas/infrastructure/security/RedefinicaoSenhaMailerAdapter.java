package br.com.unifinancas.infrastructure.security;

import br.com.unifinancas.application.notificacao.EnvioNotificacaoPort;
import br.com.unifinancas.application.usuario.RedefinicaoSenhaMailerPort;
import org.springframework.stereotype.Component;

/**
 * Implementacao de {@link RedefinicaoSenhaMailerPort} (T1.1.10). Nao fala
 * com nenhum provedor diretamente: compoe o assunto/corpo e delega ao
 * {@link EnvioNotificacaoPort}, que ja tem o caminho Resend -> fallback SMTP
 * (T1.5.6) - assim ha um unico lugar no codigo que sabe enviar e-mail.
 */
@Component
public class RedefinicaoSenhaMailerAdapter implements RedefinicaoSenhaMailerPort {

    private final EnvioNotificacaoPort envioNotificacaoPort;

    public RedefinicaoSenhaMailerAdapter(EnvioNotificacaoPort envioNotificacaoPort) {
        this.envioNotificacaoPort = envioNotificacaoPort;
    }

    @Override
    public void enviarLinkRedefinicao(String email, String nomeUsuario, String linkRedefinicao) {
        String titulo = "Redefinicao de senha - Unifinancas";
        String corpo = """
                Ola, %s.

                Recebemos um pedido para redefinir a senha da sua conta no Unifinancas.
                Para criar uma nova senha, acesse o link abaixo (valido por tempo limitado):

                %s

                Se voce nao fez este pedido, ignore este e-mail - sua senha atual continua valendo.
                """.formatted(nomeUsuario, linkRedefinicao);
        envioNotificacaoPort.enviarEmail(email, titulo, corpo);
    }
}
