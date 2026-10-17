package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.categoria.TipoCategoria;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * T1.9.6 - PATCH /v1/categorias/{id}. {@code nome}/{@code tipo} so sao
 * obrigatorios quando {@code acao=EDITAR} (validado no controller).
 * [Gap identificado pelo usuario]: {@code categoriaPaiId} agora e editavel
 * (nulo = vira categoria raiz); a deteccao de ciclo e feita no
 * {@code CategoriaService}, nao aqui.
 */
public record AtualizarCategoriaRequest(
        @NotNull(message = "acao e obrigatoria") AcaoCategoria acao, String nome, TipoCategoria tipo,
        UUID categoriaPaiId) {
}
