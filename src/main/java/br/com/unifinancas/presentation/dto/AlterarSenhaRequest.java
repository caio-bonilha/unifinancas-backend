package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * SAD §7.2, §6.8 - PATCH /v1/usuarios/me/senha (T1.1.10). Exige a senha
 * atual (RN-14 (d)); a nova segue a mesma politica do cadastro (RN-14 (e)).
 */
public record AlterarSenhaRequest(
        @NotBlank(message = "senhaAtual e obrigatoria") String senhaAtual,
        @NotBlank(message = "novaSenha e obrigatoria")
        @Size(min = 8, max = 100, message = "senha deve ter entre 8 e 100 caracteres") String novaSenha) {
}
