package br.com.unifinancas.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

class JwtTokenServiceTest {

    private RefreshTokenRepository refreshTokenRepository;
    private JwtTokenService service;

    @BeforeEach
    void montarServico() throws Exception {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        JwtProperties properties = new JwtProperties();
        properties.setKeyId("teste");
        properties.setAccessTokenValidade(Duration.ofMinutes(15));
        properties.setRefreshTokenValidade(Duration.ofDays(30));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        service = new JwtTokenService(keyPair, properties, refreshTokenRepository);
    }

    @Test
    void accessTokenGeradoERoundtripDevolveOMesmoUsuarioId() {
        UUID usuarioId = UUID.randomUUID();

        ParDeTokens par = service.emitirParDeTokens(usuarioId);

        assertThat(service.validarAccessTokenEExtrairUsuarioId(par.accessToken())).isEqualTo(usuarioId);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void accessTokenAdulteradoLancaBadCredentials() {
        UUID usuarioId = UUID.randomUUID();
        ParDeTokens par = service.emitirParDeTokens(usuarioId);
        String adulterado = par.accessToken().substring(0, par.accessToken().length() - 2) + "xx";

        assertThatThrownBy(() -> service.validarAccessTokenEExtrairUsuarioId(adulterado))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void accessTokenExpiradoLancaBadCredentials() throws Exception {
        JwtProperties properties = new JwtProperties();
        properties.setAccessTokenValidade(Duration.ofMillis(1));
        properties.setRefreshTokenValidade(Duration.ofDays(30));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        JwtTokenService servicoDeVidaCurta =
                new JwtTokenService(generator.generateKeyPair(), properties, refreshTokenRepository);

        ParDeTokens par = servicoDeVidaCurta.emitirParDeTokens(UUID.randomUUID());
        Thread.sleep(20);

        assertThatThrownBy(() -> servicoDeVidaCurta.validarAccessTokenEExtrairUsuarioId(par.accessToken()))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rotacionarRevogaOTokenAtualEEmiteUmNovoNaMesmaFamilia() {
        UUID usuarioId = UUID.randomUUID();
        UUID familiaId = UUID.randomUUID();
        RefreshToken existente = RefreshToken.novo(usuarioId, familiaId, "hash-qualquer", Instant.now().plusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existente));

        ParDeTokens novoPar = service.rotacionar("token-bruto-qualquer");

        assertThat(existente.isRevogado()).isTrue();
        assertThat(service.validarAccessTokenEExtrairUsuarioId(novoPar.accessToken())).isEqualTo(usuarioId);
        assertThat(novoPar.usuarioId()).isEqualTo(usuarioId);
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void rotacionarComTokenJaRevogadoRevogaAFamiliaInteiraELancaBadCredentials() {
        UUID familiaId = UUID.randomUUID();
        RefreshToken jaRevogado = RefreshToken.novo(UUID.randomUUID(), familiaId, "hash", Instant.now().plusSeconds(60));
        jaRevogado.revogar();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(jaRevogado));

        assertThatThrownBy(() -> service.rotacionar("token-roubado"))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenRepository).revogarFamilia(familiaId);
    }

    @Test
    void rotacionarComTokenExpiradoLancaBadCredentials() {
        RefreshToken expirado = RefreshToken.novo(UUID.randomUUID(), UUID.randomUUID(), "hash", Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> service.rotacionar("token-expirado"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rotacionarComTokenDesconhecidoLancaBadCredentials() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotacionar("token-nunca-emitido"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
