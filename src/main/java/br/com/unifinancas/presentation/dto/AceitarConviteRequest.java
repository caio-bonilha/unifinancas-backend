package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** SAD §7.2 - POST /v1/ambientes/{id}/convites/aceitar. */
public record AceitarConviteRequest(@NotBlank(message = "token e obrigatorio") String token) {
}
