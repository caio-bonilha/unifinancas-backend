package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "email e obrigatorio") @Email(message = "email invalido") String email,
        @NotBlank(message = "senha e obrigatoria") String senha) {
}
