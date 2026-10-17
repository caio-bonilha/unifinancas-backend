package br.com.unifinancas.presentation.dto;

/** Um item de {@link ErroResponse#detalhes()} (SAD Sec7.5) - ex.: campo de validacao invalido. */
public record ErroDetalhe(String campo, String erro) {
}
