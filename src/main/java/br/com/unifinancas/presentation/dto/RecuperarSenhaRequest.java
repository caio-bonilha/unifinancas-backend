package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** SAD §7.2, §6.7 - POST /v1/auth/recuperar-senha (T1.1.10). */
public record RecuperarSenhaRequest(
        @NotBlank(message = "email e obrigatorio") @Email(message = "email invalido") String email) {
}
