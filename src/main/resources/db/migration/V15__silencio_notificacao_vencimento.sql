-- T1.5.7 (decisao consultada com o usuario): opt-out individual do aviso
-- recorrente de vencimento proximo (Sec6.5/T1.5.4) - cada usuario decide,
-- por Lancamento, se quer continuar recebendo o aviso; nao afeta os
-- demais membros do Ambiente Financeiro (escopo por usuario, nao global).
CREATE TABLE silencio_notificacao_vencimento (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    usuario_id UUID NOT NULL REFERENCES usuario (id),
    lancamento_id UUID NOT NULL REFERENCES lancamento (id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_silencio_usuario_lancamento UNIQUE (usuario_id, lancamento_id)
);

CREATE INDEX idx_silencio_usuario ON silencio_notificacao_vencimento (usuario_id);
CREATE INDEX idx_silencio_lancamento ON silencio_notificacao_vencimento (lancamento_id);
