package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** SAD §7.2 - POST /v1/auth/ativar-convite (RF-04, e-mail sem conta previa). */
public record AtivarConviteRequest(
        @NotBlank(message = "token e obrigatorio") String token,
        @NotBlank(message = "nome e obrigatorio") String nome,
        @NotBlank(message = "senha e obrigatoria")
        @Size(min = 8, max = 100, message = "senha deve ter entre 8 e 100 caracteres") String senha) {
}
