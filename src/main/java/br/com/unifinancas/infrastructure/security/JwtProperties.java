package br.com.unifinancas.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do JWT (SAD Sec7.4, Sec8.5) - chaves RSA em PEM (Base64, com
 * ou sem os cabecalhos -----BEGIN/END-----) vindas de variavel de ambiente,
 * nunca de codigo-fonte. Se nao configuradas, {@link JwtKeyConfig} gera um
 * par efemero (uso local/dev apenas).
 */
@ConfigurationProperties(prefix = "unifinancas.jwt")
public class JwtProperties {

    private String chavePrivada;
    private String chavePublica;
    private String keyId = "unifinancas-dev";
    private Duration accessTokenValidade = Duration.ofMinutes(15);
    private Duration refreshTokenValidade = Duration.ofDays(30);

    public String getChavePrivada() {
        return chavePrivada;
    }

    public void setChavePrivada(String chavePrivada) {
        this.chavePrivada = chavePrivada;
    }

    public String getChavePublica() {
        return chavePublica;
    }

    public void setChavePublica(String chavePublica) {
        this.chavePublica = chavePublica;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public Duration getAccessTokenValidade() {
        return accessTokenValidade;
    }

    public void setAccessTokenValidade(Duration accessTokenValidade) {
        this.accessTokenValidade = accessTokenValidade;
    }

    public Duration getRefreshTokenValidade() {
        return refreshTokenValidade;
    }

    public void setRefreshTokenValidade(Duration refreshTokenValidade) {
        this.refreshTokenValidade = refreshTokenValidade;
    }
}
