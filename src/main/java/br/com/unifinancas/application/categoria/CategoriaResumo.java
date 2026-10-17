package br.com.unifinancas.application.categoria;

import br.com.unifinancas.domain.categoria.TipoCategoria;
import java.util.List;
import java.util.UUID;

/** {@code subcategorias} e preenchida na listagem em arvore (SAD Sec7.2); vazia ao criar uma categoria nova. */
public record CategoriaResumo(
        UUID id, String nome, TipoCategoria tipo, UUID categoriaPaiId, boolean ativa,
        List<CategoriaResumo> subcategorias) {
}
