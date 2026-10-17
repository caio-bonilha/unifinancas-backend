package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.application.notificacao.PlataformaDispositivo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** T1.5.6 - POST /v1/dispositivos. */
public record RegistrarDispositivoRequest(
        @NotBlank(message = "token e obrigatorio") String token,
        @NotNull(message = "plataforma e obrigatoria") PlataformaDispositivo plataforma) {
}
