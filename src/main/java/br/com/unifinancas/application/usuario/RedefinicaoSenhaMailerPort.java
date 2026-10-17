package br.com.unifinancas.application.usuario;

/**
 * Porta para o e-mail transacional de redefinicao de senha (SAD Sec6.7,
 * T1.1.10 - decisao consultada com o usuario: e-mail dedicado, nao uma
 * linha de Notificacao). Implementada em infrastructure reaproveitando o
 * mesmo caminho Resend -> fallback SMTP ja usado pelas notificacoes
 * (EnvioNotificacaoPort, T1.5.6).
 */
public interface RedefinicaoSenhaMailerPort {

    /**
     * @param email destinatario (e-mail do Usuario)
     * @param nomeUsuario nome do Usuario, para personalizar a mensagem
     * @param linkRedefinicao URL ja montada que leva a tela de redefinicao com o token
     */
    void enviarLinkRedefinicao(String email, String nomeUsuario, String linkRedefinicao);
}
