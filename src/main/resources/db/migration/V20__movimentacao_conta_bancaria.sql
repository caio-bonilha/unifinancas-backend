-- Movimentacao de Conta Bancaria: razao da conta, unica fonte do saldoAtual
-- (ADR-012, RN-12, SAD Sec4.4a). Somente a tabela nesta tarefa (T1.3a.2) -
-- entidade de dominio ja existe (Movimentacao); a migracao de dados
-- retroativa (backfill a partir dos lancamentos PAGO/RECEBIDO/TRANSFERENCIA
-- existentes) e a remocao de TRANSFERENCIA/conta_bancaria_destino_id de
-- lancamento ficam para a T1.3a.3.
CREATE TABLE movimentacao_conta_bancaria (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    conta_bancaria_id UUID NOT NULL REFERENCES conta_bancaria (id),
    tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('CREDITO', 'DEBITO')),
    valor NUMERIC(15, 2) NOT NULL CHECK (valor > 0),
    data DATE NOT NULL,
    descricao VARCHAR(200) NOT NULL,
    lancamento_id UUID REFERENCES lancamento (id), -- preenchido quando originada da liquidacao de um Lancamento (RN-12)
    grupo_transferencia_id UUID, -- preenchido apenas em Movimentacoes de Transferencia (as duas pernas do par, ADR-012)
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Sustenta o extrato da Conta Bancaria (GET /v1/contas-bancarias/{id}/movimentacoes, SAD Sec7.2) e o recalculo de saldo (RN-12).
CREATE INDEX idx_movimentacao_ambiente_conta_data ON movimentacao_conta_bancaria (ambiente_financeiro_id, conta_bancaria_id, data);

-- Sustenta REABRIR (RN-12): localizar/remover a Movimentacao vinculada a um Lancamento liquidado.
CREATE UNIQUE INDEX idx_movimentacao_lancamento ON movimentacao_conta_bancaria (lancamento_id) WHERE lancamento_id IS NOT NULL;

-- Sustenta consultar as duas pernas de uma Transferencia pelo grupo.
CREATE INDEX idx_movimentacao_grupo_transferencia ON movimentacao_conta_bancaria (grupo_transferencia_id) WHERE grupo_transferencia_id IS NOT NULL;
