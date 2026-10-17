package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.categoria.TipoCategoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** SAD §7.2 - POST /v1/categorias. {@code categoriaPaiId} e opcional (ausente = categoria raiz). */
public record CriarCategoriaRequest(
        @NotBlank(message = "nome e obrigatorio") String nome,
        @NotNull(message = "tipo e obrigatorio") TipoCategoria tipo,
        UUID categoriaPaiId) {
}
