-- T1.3a.4, ADR-012: conclui a remocao de TRANSFERENCIA de Lancamento
-- Financeiro e de Lancamento Recorrente - a V21 ja migrou/removeu os dados
-- historicos de Lancamento; esta migracao so mexe em schema, feita junto da
-- entidade LancamentoFinanceiro/LancamentoRecorrente revisada (nao antes),
-- para nao existir uma janela em que o app ainda mapeia uma coluna/valor
-- que o banco ja removeu.

-- Lancamento Recorrente TRANSFERENCIA nunca tem uma Movimentacao associada
-- (e so uma regra futura, nao uma transacao ja ocorrida) - agendamento de
-- transferencia recorrente fica fora de escopo (ADR-012); as linhas
-- existentes desse tipo sao removidas.
DELETE FROM lancamento_recorrente WHERE tipo = 'TRANSFERENCIA';

ALTER TABLE lancamento DROP COLUMN conta_bancaria_destino_id;
ALTER TABLE lancamento DROP CONSTRAINT lancamento_tipo_check;
ALTER TABLE lancamento ADD CONSTRAINT lancamento_tipo_check CHECK (tipo IN ('RECEITA', 'DESPESA'));

ALTER TABLE lancamento_recorrente DROP COLUMN conta_bancaria_destino_id;
ALTER TABLE lancamento_recorrente DROP CONSTRAINT lancamento_recorrente_tipo_check;
ALTER TABLE lancamento_recorrente ADD CONSTRAINT lancamento_recorrente_tipo_check CHECK (tipo IN ('RECEITA', 'DESPESA'));
