package br.com.unifinancas.presentation.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record GastoPorCategoriaResponse(UUID categoriaId, String categoriaNome, BigDecimal valor) {
}
