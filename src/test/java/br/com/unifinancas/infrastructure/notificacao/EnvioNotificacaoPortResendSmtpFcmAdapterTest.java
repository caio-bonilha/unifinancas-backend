package br.com.unifinancas.infrastructure.notificacao;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.FirebaseMessaging;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * T1.5.6 (revisao pos-implementacao, decisao consultada com o usuario):
 * Resend como provedor primario de e-mail, com fallback automatico para
 * SMTP quando o Resend nao esta configurado ou falha.
 */
@ExtendWith(MockitoExtension.class)
class EnvioNotificacaoPortResendSmtpFcmAdapterTest {

    private static final String REMETENTE = "unifinancas@example.com";

    @Mock
    private Resend resend;

    @Mock
    private Emails emails;

    @Mock
    private JavaMailSender mailSender;

    @Test
    void quandoResendConfiguradoEEnviaComSucesso_naoUsaSmtp() throws ResendException {
        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(new CreateEmailResponse("id-1"));

        var adapter = new EnvioNotificacaoPortResendSmtpFcmAdapter(
                Optional.of(resend), mailSender, REMETENTE, Optional.empty());

        adapter.enviarEmail("destino@teste.com", "Assunto", "Mensagem");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void quandoResendConfiguradoEFalha_usaFallbackSmtp() throws ResendException {
        when(resend.emails()).thenReturn(emails);
        when(emails.send(any(CreateEmailOptions.class))).thenThrow(new ResendException("falha simulada", null));

        var adapter = new EnvioNotificacaoPortResendSmtpFcmAdapter(
                Optional.of(resend), mailSender, REMETENTE, Optional.empty());

        assertThatCode(() -> adapter.enviarEmail("destino@teste.com", "Assunto", "Mensagem")).doesNotThrowAnyException();

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void quandoResendNaoConfigurado_usaSmtpDireto() {
        var adapter = new EnvioNotificacaoPortResendSmtpFcmAdapter(
                Optional.empty(), mailSender, REMETENTE, Optional.empty());

        adapter.enviarEmail("destino@teste.com", "Assunto", "Mensagem");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void enviarPushSemFcmConfigurado_lancaExcecaoClara() {
        var adapter = new EnvioNotificacaoPortResendSmtpFcmAdapter(
                Optional.empty(), mailSender, REMETENTE, Optional.<FirebaseMessaging>empty());

        assertThatCode(() -> adapter.enviarPush(java.util.List.of("token-1"), "Assunto", "Mensagem"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FCM nao configurado");
    }
}
