package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.categoria.TipoCategoria;
import java.util.List;
import java.util.UUID;

/** SAD §7.2, §4.10 - {@code subcategorias} aninhadas recursivamente (listagem em arvore). */
public record CategoriaResponse(
        UUID id, String nome, TipoCategoria tipo, UUID categoriaPaiId, boolean ativa,
        List<CategoriaResponse> subcategorias) {
}
