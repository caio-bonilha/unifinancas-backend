package br.com.unifinancas.infrastructure.notificacao;

import br.com.unifinancas.application.notificacao.EnvioNotificacaoPort;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Implementacao real de {@link EnvioNotificacaoPort} (T1.5.6, decisao
 * consultada com o usuario): push via Firebase Cloud Messaging, e-mail via
 * **Resend como provedor primario** (decisao consultada com o usuario,
 * revisao pos-T1.5.6) com **fallback automatico para SMTP generico**
 * ({@link JavaMailSender}, qualquer provedor) quando o Resend nao esta
 * configurado ou a chamada falha. Uma falha do fallback SMTP (ou de PUSH)
 * propaga para {@code EnvioNotificacaoJobHandler}, que deixa o {@code
 * JobWorker} (T0.3.3) marcar o job como ERRO - nao ha fallback silencioso
 * *dentro* deste adapter para alem do par Resend/SMTP porque ja existe uma
 * rede de seguranca estrutural: a linha IN_APP da mesma Notificacao (T1.5.2)
 * e independente e ja foi persistida, entao o usuario nunca deixa de ver o
 * aviso so porque o envio de PUSH/EMAIL falhou (SAD Sec7.6).
 */
@Component
public class EnvioNotificacaoPortResendSmtpFcmAdapter implements EnvioNotificacaoPort {

    private static final Logger log = LoggerFactory.getLogger(EnvioNotificacaoPortResendSmtpFcmAdapter.class);

    private final Optional<Resend> resend;
    private final JavaMailSender mailSender;
    private final String remetente;
    private final Optional<FirebaseMessaging> firebaseMessaging;

    public EnvioNotificacaoPortResendSmtpFcmAdapter(Optional<Resend> resend, JavaMailSender mailSender,
            @Value("${unifinancas.mail.remetente}") String remetente,
            Optional<FirebaseMessaging> firebaseMessaging) {
        this.resend = resend;
        this.mailSender = mailSender;
        this.remetente = remetente;
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public void enviarEmail(String destinatario, String titulo, String mensagem) {
        if (resend.isPresent()) {
            try {
                enviarEmailViaResend(resend.get(), destinatario, titulo, mensagem);
                return;
            } catch (ResendException e) {
                log.warn("Falha ao enviar e-mail via Resend para {} - usando fallback SMTP.", destinatario, e);
            }
        }
        enviarEmailViaSmtp(destinatario, titulo, mensagem);
    }

    private void enviarEmailViaResend(Resend cliente, String destinatario, String titulo, String mensagem)
            throws ResendException {
        CreateEmailOptions opcoes = CreateEmailOptions.builder()
                .from(remetente)
                .to(destinatario)
                .subject(titulo)
                .text(mensagem)
                .build();
        cliente.emails().send(opcoes);
    }

    private void enviarEmailViaSmtp(String destinatario, String titulo, String mensagem) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(remetente);
        email.setTo(destinatario);
        email.setSubject(titulo);
        email.setText(mensagem);
        mailSender.send(email);
    }

    @Override
    public void enviarPush(List<String> tokensDispositivo, String titulo, String mensagem) {
        FirebaseMessaging messaging = firebaseMessaging.orElseThrow(() -> new IllegalStateException(
                "FCM nao configurado (unifinancas.fcm.credenciais-path) - nao e possivel enviar PUSH."));

        Notification notification = Notification.builder().setTitle(titulo).setBody(mensagem).build();
        MulticastMessage message = MulticastMessage.builder().setNotification(notification).addAllTokens(tokensDispositivo).build();
        try {
            messaging.sendEachForMulticast(message);
        } catch (FirebaseMessagingException e) {
            throw new IllegalStateException("Falha ao enviar PUSH via FCM.", e);
        }
    }
}
