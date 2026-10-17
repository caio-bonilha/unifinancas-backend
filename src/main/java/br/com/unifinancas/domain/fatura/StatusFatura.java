package br.com.unifinancas.domain.fatura;

/** SAD Sec4.6/Sec6.5: ciclo de vida da Fatura - ver diagrama de estados na Sec6.5. */
public enum StatusFatura {
    ABERTA,
    FECHADA,
    PAGA,
    PAGA_PARCIAL,
    ATRASADA
}
