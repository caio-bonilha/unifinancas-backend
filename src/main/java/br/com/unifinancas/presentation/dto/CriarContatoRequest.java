package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.contato.TipoContato;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** SAD §7.2 - POST /v1/contatos. {@code documento}, {@code email} e {@code telefone} sao opcionais. */
public record CriarContatoRequest(
        @NotBlank(message = "nome e obrigatorio") String nome,
        @NotNull(message = "tipo e obrigatorio") TipoContato tipo,
        String documento,
        @Email(message = "email invalido") String email,
        String telefone) {
}
