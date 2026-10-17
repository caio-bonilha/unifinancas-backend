package br.com.unifinancas.presentation.dto;

/** SAD §7.2 - PATCH /v1/cartoes/{id}: uma unica rota cobre editar campos, ativar e desativar (mesmo padrao de /v1/contas-bancarias). */
public enum AcaoCartao {
    EDITAR,
    ATIVAR,
    DESATIVAR
}
