-- T3.1.6, SAD Sec4.16/Sec6.3 (Fluxo 6.3, passo 6): novo tipo de Notificacao
-- para "documento processado, aguardando revisao do usuario". O CHECK da V12
-- nasceu inline (nome auto-gerado pelo Postgres: notificacao_tipo_check);
-- aqui ele e refeito com nome explicito, ja incluindo o valor novo.
ALTER TABLE notificacao DROP CONSTRAINT IF EXISTS notificacao_tipo_check;

ALTER TABLE notificacao
    ADD CONSTRAINT notificacao_tipo_check CHECK (tipo IN (
        'VENCIMENTO_CONTA',
        'ORCAMENTO_LIMITE',
        'FATURA_VENCIMENTO',
        'FALHA_PROCESSAMENTO',
        'DOCUMENTO_AGUARDANDO_REVISAO',
        'OUTRO'
    ));
