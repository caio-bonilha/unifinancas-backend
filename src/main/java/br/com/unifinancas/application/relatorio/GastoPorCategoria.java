package br.com.unifinancas.application.relatorio;

import java.math.BigDecimal;
import java.util.UUID;

/** T1.7.1, RF-15: um item da "visao por categoria" do dashboard - despesas do periodo vigente agrupadas por categoria. */
public record GastoPorCategoria(UUID categoriaId, String categoriaNome, BigDecimal valor) {
}
