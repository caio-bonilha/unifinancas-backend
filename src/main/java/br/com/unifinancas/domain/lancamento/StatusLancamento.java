package br.com.unifinancas.domain.lancamento;

/** SAD Sec4.7, Sec6.5 (diagrama de estados). */
public enum StatusLancamento {
    PENDENTE,
    PAGO,
    RECEBIDO,
    CANCELADO,
    ATRASADO
}
