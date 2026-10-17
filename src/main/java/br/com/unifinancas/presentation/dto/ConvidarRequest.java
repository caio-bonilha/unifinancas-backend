package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** SAD §7.2 - POST /v1/ambientes/{id}/convites (RF-04). */
public record ConvidarRequest(
        @NotBlank(message = "email e obrigatorio") @Email(message = "email invalido") String email,
        @NotNull(message = "perfil e obrigatorio") PerfilUsuarioAmbiente perfil) {
}
