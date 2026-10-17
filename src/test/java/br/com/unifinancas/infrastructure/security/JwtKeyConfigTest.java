package br.com.unifinancas.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtKeyConfigTest {

    private final JwtKeyConfig config = new JwtKeyConfig();

    @Test
    void semChavesConfiguradasGeraUmParEfemeroValido() {
        JwtProperties properties = new JwtProperties();

        KeyPair par = config.jwtKeyPair(properties);

        assertThat(par.getPrivate()).isNotNull();
        assertThat(par.getPublic()).isNotNull();
    }

    @Test
    void comChavesConfiguradasEmPemCarregaOMesmoParOriginal() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair original = generator.generateKeyPair();

        String pemPrivada = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(original.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        String pemPublica = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(original.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";

        JwtProperties properties = new JwtProperties();
        properties.setChavePrivada(pemPrivada);
        properties.setChavePublica(pemPublica);

        KeyPair carregado = config.jwtKeyPair(properties);

        assertThat(carregado.getPrivate()).isEqualTo(original.getPrivate());
        assertThat(carregado.getPublic()).isEqualTo(original.getPublic());
    }
}
