package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * SAD §7.2, §6.7 - POST /v1/auth/redefinir-senha (T1.1.10). Mesma politica
 * de senha do cadastro (RN-14 (e)): 8 a 100 caracteres.
 */
public record RedefinirSenhaRequest(
        @NotBlank(message = "token e obrigatorio") String token,
        @NotBlank(message = "novaSenha e obrigatoria")
        @Size(min = 8, max = 100, message = "senha deve ter entre 8 e 100 caracteres") String novaSenha) {
}
