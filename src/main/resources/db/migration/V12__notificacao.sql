-- Notificacao: alerta gerado a partir de eventos do dominio (RF-14, SAD Sec4.16).
-- referencia_tipo/referencia_id sao uma referencia polimorfica ao objeto de
-- origem (ex.: lancamento, orcamento_meta, fatura) - sem FK de banco,
-- resolvida em application (Sec5.2).
CREATE TABLE notificacao (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    usuario_id UUID NOT NULL REFERENCES usuario (id),
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('VENCIMENTO_CONTA', 'ORCAMENTO_LIMITE', 'FATURA_VENCIMENTO', 'FALHA_PROCESSAMENTO', 'OUTRO')),
    titulo VARCHAR(150) NOT NULL,
    mensagem TEXT NOT NULL,
    referencia_tipo VARCHAR(30),
    referencia_id UUID,
    canal VARCHAR(10) NOT NULL CHECK (canal IN ('PUSH', 'EMAIL', 'IN_APP')),
    lida BOOLEAN NOT NULL DEFAULT FALSE,
    enviada_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notificacao_usuario_lida ON notificacao (usuario_id, lida);
