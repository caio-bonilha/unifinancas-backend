package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * SAD Fluxo 6.1. Politica de senha minima (SAD nao especifica uma - 8
 * caracteres e o piso comumente aceito, ex.: NIST 800-63B).
 */
public record CadastroRequest(
        @NotBlank(message = "nome e obrigatorio") String nome,
        @NotBlank(message = "email e obrigatorio") @Email(message = "email invalido") String email,
        @NotBlank(message = "senha e obrigatoria")
        @Size(min = 8, max = 100, message = "senha deve ter entre 8 e 100 caracteres") String senha) {
}
