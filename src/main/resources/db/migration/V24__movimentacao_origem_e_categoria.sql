-- ADR-014, T1.3b.2: Movimentacao ganha `origem` (LIQUIDACAO/TRANSFERENCIA/MANUAL)
-- e `categoria_id` opcional (so usado quando origem=MANUAL - Movimentacao de
-- Lancamento/Transferencia nao tem categoria propria, quem tem e o Lancamento).
-- `origem` e derivavel dos dados ja existentes: lancamento_id preenchido ->
-- LIQUIDACAO, grupo_transferencia_id preenchido -> TRANSFERENCIA; nenhuma
-- linha existente fica MANUAL (essa origem so passa a existir a partir de
-- T1.3b.4).
ALTER TABLE movimentacao_conta_bancaria
    ADD COLUMN origem VARCHAR(15),
    ADD COLUMN categoria_id UUID REFERENCES categoria (id);

UPDATE movimentacao_conta_bancaria
SET origem = CASE
    WHEN lancamento_id IS NOT NULL THEN 'LIQUIDACAO'
    WHEN grupo_transferencia_id IS NOT NULL THEN 'TRANSFERENCIA'
    ELSE 'MANUAL'
END;

ALTER TABLE movimentacao_conta_bancaria
    ALTER COLUMN origem SET NOT NULL,
    ADD CONSTRAINT chk_movimentacao_origem CHECK (origem IN ('LIQUIDACAO', 'TRANSFERENCIA', 'MANUAL'));

-- Sustenta o relatorio por categoria (RelatorioService, GET /v1/dashboard) somando Movimentacao avulsa com categoria preenchida.
CREATE INDEX idx_movimentacao_categoria ON movimentacao_conta_bancaria (ambiente_financeiro_id, categoria_id) WHERE categoria_id IS NOT NULL;
