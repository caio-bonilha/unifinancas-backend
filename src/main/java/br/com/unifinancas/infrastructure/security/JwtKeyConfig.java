package br.com.unifinancas.infrastructure.security;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Par de chaves RSA usado para assinar/validar o JWT (SAD Sec7.4, ADR-010).
 * Em producao, {@code unifinancas.jwt.chave-privada}/{@code chave-publica}
 * devem vir de variavel de ambiente (Sec8.5 - segredo nunca em
 * codigo-fonte). Sem elas configuradas, gera um par efemero por processo -
 * conveniente para desenvolvimento local, mas os tokens emitidos deixam de
 * ser validos a cada reinicio, e nunca deve ser usado assim em producao.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    public KeyPair jwtKeyPair(JwtProperties properties) {
        if (StringUtils.hasText(properties.getChavePrivada()) && StringUtils.hasText(properties.getChavePublica())) {
            return carregarDePem(properties.getChavePrivada(), properties.getChavePublica());
        }
        log.warn("unifinancas.jwt.chave-privada/chave-publica nao configuradas - gerando um par de chaves RSA "
                + "efemero para este processo (SAD Sec8.5). NUNCA use isso em producao: tokens emitidos deixam "
                + "de ser validos a cada reinicio, e cada instancia teria uma chave diferente.");
        return gerarParEfemero();
    }

    private KeyPair gerarParEfemero() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo RSA indisponivel na JVM.", e);
        }
    }

    private KeyPair carregarDePem(String chavePrivadaPem, String chavePublicaPem) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            byte[] bytesPrivada = Base64.getDecoder().decode(limparPem(chavePrivadaPem));
            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytesPrivada));

            byte[] bytesPublica = Base64.getDecoder().decode(limparPem(chavePublicaPem));
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(bytesPublica));

            return new KeyPair(publicKey, privateKey);
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel carregar o par de chaves RSA do JWT a partir de "
                    + "unifinancas.jwt.chave-privada/chave-publica.", e);
        }
    }

    private String limparPem(String pem) {
        return pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
    }
}
