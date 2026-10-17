package br.com.unifinancas.domain.lancamento;

/** SAD Sec4.7: de onde o Lancamento nasceu - so MANUAL tem factory ate a Etapa 1.6/1.8 e a Fase 2 existirem. */
public enum OrigemLancamento {
    MANUAL,
    COMPRA,
    VENDA,
    RECORRENCIA,
    IMPORTACAO,
    DOCUMENTO,
    /** ADR-017, T2.1.3/T2.1.4: juros rotativo de Fatura paga parcialmente, ou uma parcela do parcelamento de Fatura em atraso. */
    FATURA
}
