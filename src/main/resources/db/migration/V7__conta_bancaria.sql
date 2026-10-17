-- Conta Bancaria: origem/destino de recursos dentro de um Ambiente
-- Financeiro (RF-05, SAD Sec4.4). saldo_atual e derivado (recalculado a
-- partir de saldo_inicial + soma dos lancamentos confirmados) - nunca
-- editado diretamente pelo usuario; no MVP nasce igual a saldo_inicial
-- porque ainda nao ha Lancamento (Etapa 1.3).
CREATE TABLE conta_bancaria (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    nome VARCHAR(100) NOT NULL,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('CORRENTE', 'POUPANCA', 'DINHEIRO', 'CARTEIRA_DIGITAL', 'INVESTIMENTO')),
    saldo_inicial NUMERIC(15, 2) NOT NULL DEFAULT 0,
    saldo_atual NUMERIC(15, 2) NOT NULL DEFAULT 0,
    moeda CHAR(3) NOT NULL DEFAULT 'BRL',
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conta_bancaria_ambiente ON conta_bancaria (ambiente_financeiro_id);
