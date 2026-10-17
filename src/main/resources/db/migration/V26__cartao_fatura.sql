-- Cartao e Fatura (RF-17, SAD Sec4.5/4.6, ADR-017). T2.1.1.
CREATE TABLE cartao (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    conta_bancaria_id UUID REFERENCES conta_bancaria (id), -- conta de pagamento, opcional (Sec4.5)
    nome VARCHAR(100) NOT NULL,
    limite NUMERIC(15, 2) NOT NULL CHECK (limite > 0),
    dia_fechamento SMALLINT NOT NULL CHECK (dia_fechamento BETWEEN 1 AND 31),
    dia_vencimento SMALLINT NOT NULL CHECK (dia_vencimento BETWEEN 1 AND 31),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    -- ADR-017: taxa mensal do rotativo, configuravel por Cartao (nao fixa do sistema nem por Ambiente).
    taxa_juros_rotativo_mensal NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (taxa_juros_rotativo_mensal >= 0),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_cartao_ambiente ON cartao (ambiente_financeiro_id);

-- Sec5.2: fatura nao tem ambiente_financeiro_id proprio - isolamento por tenant passa por JOIN com cartao (ADR-017).
CREATE TABLE fatura (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    cartao_id UUID NOT NULL REFERENCES cartao (id),
    competencia DATE NOT NULL, -- primeiro dia do mes de referencia
    data_fechamento DATE NOT NULL,
    data_vencimento DATE NOT NULL,
    valor_total NUMERIC(15, 2) NOT NULL DEFAULT 0,
    valor_pago NUMERIC(15, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ABERTA', 'FECHADA', 'PAGA', 'PAGA_PARCIAL', 'ATRASADA')),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fatura_cartao_competencia UNIQUE (cartao_id, competencia)
);

-- Sustenta o job de fechamento/atraso (T2.1.2) e a notificacao de vencimento de fatura (RF-14, fora de escopo desta tarefa).
CREATE INDEX idx_fatura_status_vencimento ON fatura (status, data_vencimento);

-- FKs adiadas desde a T1.3.1 (V10) - cartao/fatura agora existem (decisao de sequenciamento ja registrada la).
ALTER TABLE lancamento ADD CONSTRAINT fk_lancamento_cartao FOREIGN KEY (cartao_id) REFERENCES cartao (id);
ALTER TABLE lancamento ADD CONSTRAINT fk_lancamento_fatura FOREIGN KEY (fatura_id) REFERENCES fatura (id);

-- ADR-017: origem ganha FATURA - Lancamento de juros rotativo ou de uma parcela do parcelamento de Fatura em atraso.
ALTER TABLE lancamento DROP CONSTRAINT lancamento_origem_check;
ALTER TABLE lancamento ADD CONSTRAINT lancamento_origem_check
    CHECK (origem IN ('MANUAL', 'COMPRA', 'VENDA', 'RECORRENCIA', 'IMPORTACAO', 'DOCUMENTO', 'FATURA'));
