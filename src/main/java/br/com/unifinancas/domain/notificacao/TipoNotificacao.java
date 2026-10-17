package br.com.unifinancas.domain.notificacao;

/** SAD Sec4.16, Sec5.2 (o check de banco inclui OUTRO como catch-all, alem dos tipos descritos no texto). */
public enum TipoNotificacao {
    VENCIMENTO_CONTA,
    ORCAMENTO_LIMITE,
    FATURA_VENCIMENTO,
    FALHA_PROCESSAMENTO,
    /** T3.1.6, Fluxo 6.3 passo 6: documento processado, aguardando revisao do usuario que fez o upload. */
    DOCUMENTO_AGUARDANDO_REVISAO,
    OUTRO
}
