package br.com.unifinancas.presentation.dto;

/** SAD §7.2 - PATCH /v1/lancamentos-recorrentes/{id}: uma unica rota cobre as tres transicoes (T1.6.2). */
public enum AcaoLancamentoRecorrente {
    EDITAR,
    DESATIVAR,
    REATIVAR
}
