package br.com.unifinancas.presentation.dto;

/** SAD §7.2, RN-11, ADR-017 - PATCH /v1/cartoes/{id}/faturas/{faturaId}: paga (total ou parcial) ou parcela uma Fatura ATRASADA. */
public enum AcaoFatura {
    PAGAR,
    PARCELAR
}
