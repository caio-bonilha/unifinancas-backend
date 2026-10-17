package br.com.unifinancas.infrastructure.notificacao;

import com.resend.Resend;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Inicializa o cliente do Resend (provedor primario de e-mail, a pedido do
 * usuario) so quando {@code unifinancas.mail.resend.api-key} estiver
 * configurada - mesmo padrao de "null bean" de {@link FcmConfig}: sem chave,
 * a aplicacao sobe normalmente e {@link EnvioNotificacaoPortResendSmtpFcmAdapter}
 * usa direto o fallback SMTP (generico, qualquer provedor) ja existente
 * desde a T1.5.6.
 */
@Configuration
public class ResendConfig {

    @Bean
    public Resend resend(@Value("${unifinancas.mail.resend.api-key:}") String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        return new Resend(apiKey);
    }
}
