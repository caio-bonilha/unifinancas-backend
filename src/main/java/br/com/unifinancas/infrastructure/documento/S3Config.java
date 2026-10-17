package br.com.unifinancas.infrastructure.documento;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * T1.8.2: em produção, credenciais resolvidas pela cadeia padrão do AWS SDK
 * (variável de ambiente, IAM role - Sec8.5, nunca hardcoded). Não há bean
 * condicional como {@code FcmConfig}: o SDK só falha ao tentar uma chamada
 * real, não ao construir o client, então a aplicação sobe normalmente mesmo
 * sem credenciais.
 *
 * <p>{@code endpoint-override} + {@code access-key}/{@code secret-key} são
 * só para desenvolvimento/teste contra um storage local compatível com S3
 * (MinIO no {@code docker-compose.yml}, ver o profile {@code local}) - em
 * branco (default), o comportamento de produção (cadeia padrão) é preservado.
 */
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(@Value("${unifinancas.storage.s3.region}") String region,
            @Value("${unifinancas.storage.s3.endpoint-override:}") String endpointOverride,
            @Value("${unifinancas.storage.s3.access-key:}") String accessKey,
            @Value("${unifinancas.storage.s3.secret-key:}") String secretKey) {
        var builder = S3Client.builder().region(Region.of(region));
        if (StringUtils.hasText(endpointOverride)) {
            builder.endpointOverride(URI.create(endpointOverride)).forcePathStyle(true);
        }
        credenciaisEstaticas(accessKey, secretKey).ifPresent(builder::credentialsProvider);
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(@Value("${unifinancas.storage.s3.region}") String region,
            @Value("${unifinancas.storage.s3.endpoint-override:}") String endpointOverride,
            @Value("${unifinancas.storage.s3.access-key:}") String accessKey,
            @Value("${unifinancas.storage.s3.secret-key:}") String secretKey) {
        var builder = S3Presigner.builder().region(Region.of(region));
        if (StringUtils.hasText(endpointOverride)) {
            builder.endpointOverride(URI.create(endpointOverride))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        credenciaisEstaticas(accessKey, secretKey).ifPresent(builder::credentialsProvider);
        return builder.build();
    }

    /** Só quando access-key E secret-key estão preenchidas (dev/MinIO); senão, cadeia padrão do SDK (produção). */
    private static java.util.Optional<AwsCredentialsProvider> credenciaisEstaticas(String accessKey, String secretKey) {
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            return java.util.Optional.of(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        }
        return java.util.Optional.empty();
    }
}
