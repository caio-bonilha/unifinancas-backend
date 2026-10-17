package br.com.unifinancas.presentation.dto;

/**
 * SAD §7.2 - PATCH /v1/lancamentos/{id}: uma unica rota cobre as transicoes
 * (Sec4.7, T1.3.3). REABRIR: gap identificado pelo usuario - desfaz
 * CONFIRMAR_PAGAMENTO/CONFIRMAR_RECEBIMENTO (decisao consultada: PAGO/
 * RECEBIDO nao eram mais terminais como o Sec6.5 original documentava).
 */
public enum AcaoLancamento {
    EDITAR,
    CONFIRMAR_PAGAMENTO,
    CONFIRMAR_RECEBIMENTO,
    CANCELAR,
    REABRIR
}
