package br.com.unifinancas.presentation.dto;

import java.util.List;

/** SAD §7.2, RF-21, T4.1.4 - GET .../tabela: cabeçalho + linhas genéricas de um CSV/Excel, para o usuário mapear colunas na revisão. */
public record TabelaImportadaResponse(List<String> cabecalho, List<List<String>> linhas) {
}
