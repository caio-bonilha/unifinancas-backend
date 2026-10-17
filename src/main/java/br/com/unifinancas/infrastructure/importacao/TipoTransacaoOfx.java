package br.com.unifinancas.infrastructure.importacao;

/**
 * Valores fixos de {@code TRNTYPE} definidos pela especificacao OFX (Section
 * 11.4.4.3 do OFX 2.x) para uma {@code STMTTRN}. {@code OUTRO} cobre
 * qualquer valor que a especificacao nao preveja (arquivo malformado ou
 * extensao proprietaria de banco) - nao rejeita a transacao por causa disso.
 */
public enum TipoTransacaoOfx {
    CREDIT,
    DEBIT,
    INT,
    DIV,
    FEE,
    SRVCHG,
    DEP,
    ATM,
    POS,
    XFER,
    CHECK,
    PAYMENT,
    CASH,
    DIRECTDEP,
    DIRECTDEBIT,
    REPEATPMT,
    HOLD,
    OTHER
}
