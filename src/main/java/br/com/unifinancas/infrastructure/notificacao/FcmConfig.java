package br.com.unifinancas.infrastructure.notificacao;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Inicializa o Firebase Admin SDK para envio de PUSH (T1.5.6, decisao
 * consultada com o usuario) so quando {@code unifinancas.fcm.credenciais-path}
 * estiver configurado - sem isso, a aplicacao ainda sobe normalmente
 * (nenhuma credencial pode ser "gerada" como no par de chaves JWT efemero,
 * SAD Sec8.5); so uma tentativa real de enviar PUSH sem configuracao falha
 * (ver {@link EnvioNotificacaoPortResendSmtpFcmAdapter}). O metodo devolve
 * {@code null} quando nao configurado - Spring registra isso como um
 * "null bean" (sem erro), e {@code Optional<FirebaseMessaging>} injetado
 * em outro bean resolve corretamente para vazio (mesmo padrao usado pelo
 * adapter e por {@link ResendConfig}).
 */
@Configuration
public class FcmConfig {

    @Bean
    public FirebaseMessaging firebaseMessaging(@Value("${unifinancas.fcm.credenciais-path:}") String credenciaisPath) throws IOException {
        if (!StringUtils.hasText(credenciaisPath)) {
            return null;
        }
        try (FileInputStream credenciais = new FileInputStream(credenciaisPath)) {
            FirebaseOptions options = FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(credenciais)).build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty() ? FirebaseApp.initializeApp(options) : FirebaseApp.getInstance();
            return FirebaseMessaging.getInstance(app);
        }
    }
}
