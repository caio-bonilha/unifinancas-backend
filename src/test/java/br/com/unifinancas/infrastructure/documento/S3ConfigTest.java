package br.com.unifinancas.infrastructure.documento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * O ponto do bean condicional aqui é: com {@code access-key}/{@code secret-key}
 * (dev/MinIO) ou sem elas (produção — cadeia padrão), o client precisa
 * <b>construir</b> sem lançar. A chamada real ao S3 não é exercitada.
 */
class S3ConfigTest {

    private final S3Config config = new S3Config();

    @Test
    void clientEPresignerConstroemSemCredenciais() {
        assertThat(config.s3Client("us-east-1", "", "", "")).isNotNull();
        assertThat(config.s3Presigner("us-east-1", "", "", "")).isNotNull();
    }

    @Test
    void clientEPresignerConstroemComEndpointECredenciaisEstaticas() {
        assertThat(config.s3Client("us-east-1", "http://localhost:9000", "minioadmin", "minioadmin")).isNotNull();
        assertThat(config.s3Presigner("us-east-1", "http://localhost:9000", "minioadmin", "minioadmin")).isNotNull();
    }
}
