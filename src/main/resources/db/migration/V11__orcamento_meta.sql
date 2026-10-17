-- Orcamento/Meta: limite de gasto planejado por categoria/periodo, ou meta
-- de economia acompanhada ao longo do tempo (RF-12, RF-13, SAD Sec4.11).
-- categoria_id/valor_limite (ORCAMENTO) e valor_alvo (META) sao obrigatorios
-- so por tipo - constraint de aplicacao (entidade), nao de banco (Sec5.2).
CREATE TABLE orcamento_meta (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('ORCAMENTO', 'META')),
    categoria_id UUID REFERENCES categoria (id),
    nome VARCHAR(100),
    valor_limite NUMERIC(15, 2),
    valor_alvo NUMERIC(15, 2),
    periodo VARCHAR(20) NOT NULL DEFAULT 'MENSAL',
    data_inicio DATE,
    data_fim DATE,
    conta_bancaria_id UUID REFERENCES conta_bancaria (id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ATIVO', 'CONCLUIDO', 'CANCELADO')),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orcamento_meta_ambiente_tipo ON orcamento_meta (ambiente_financeiro_id, tipo);
