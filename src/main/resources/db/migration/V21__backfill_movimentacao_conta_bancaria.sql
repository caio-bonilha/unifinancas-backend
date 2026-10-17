-- ADR-012, T1.3a.3: migracao de DADOS - cria retroativamente as
-- Movimentacoes de Conta Bancaria correspondentes aos Lancamentos ja
-- liquidados, e remove os Lancamentos TRANSFERENCIA (que deixam de existir
-- como conceito). O schema da tabela lancamento NAO muda aqui (coluna
-- conta_bancaria_destino_id e o valor TRANSFERENCIA do CHECK de tipo saem
-- na T1.3a.4, junto com a entidade LancamentoFinanceiro revisada) - manter
-- schema e dados em migracoes separadas evita uma janela em que o app
-- ainda mapeia colunas/valores que o banco ja removeu.
--
-- Executado direto em SQL, sem passar pelos application services: e
-- backfill historico de dados existentes, nao um fluxo em runtime (RN-09
-- e sobre a IA conversacional, nao se aplica a migracoes de schema/dados).

-- 1) RECEITA/DESPESA ja liquidados (PAGO/RECEBIDO) com conta bancaria
--    definida -> uma Movimentacao cada, vinculada ao Lancamento de origem.
INSERT INTO movimentacao_conta_bancaria
    (ambiente_financeiro_id, conta_bancaria_id, tipo, valor, data, descricao, lancamento_id)
SELECT
    l.ambiente_financeiro_id,
    l.conta_bancaria_id,
    CASE WHEN l.tipo = 'RECEITA' THEN 'CREDITO' ELSE 'DEBITO' END,
    l.valor,
    l.data_pagamento,
    l.descricao,
    l.id
FROM lancamento l
WHERE l.tipo IN ('RECEITA', 'DESPESA')
  AND l.status IN ('PAGO', 'RECEBIDO')
  AND l.conta_bancaria_id IS NOT NULL;

-- 2) TRANSFERENCIA PAGA com origem e destino definidos -> par de
--    Movimentacoes (debito na origem, credito no destino) compartilhando um
--    grupo_transferencia_id gerado uma vez por Lancamento migrado.
CREATE TEMP TABLE tmp_transferencia_migrar AS
SELECT
    l.id,
    l.ambiente_financeiro_id,
    l.conta_bancaria_id,
    l.conta_bancaria_destino_id,
    l.valor,
    l.data_pagamento,
    l.descricao,
    uuidv7() AS grupo_transferencia_id
FROM lancamento l
WHERE l.tipo = 'TRANSFERENCIA'
  AND l.status = 'PAGO'
  AND l.conta_bancaria_id IS NOT NULL
  AND l.conta_bancaria_destino_id IS NOT NULL;

INSERT INTO movimentacao_conta_bancaria
    (ambiente_financeiro_id, conta_bancaria_id, tipo, valor, data, descricao, grupo_transferencia_id)
SELECT ambiente_financeiro_id, conta_bancaria_id, 'DEBITO', valor, data_pagamento, descricao, grupo_transferencia_id
FROM tmp_transferencia_migrar;

INSERT INTO movimentacao_conta_bancaria
    (ambiente_financeiro_id, conta_bancaria_id, tipo, valor, data, descricao, grupo_transferencia_id)
SELECT ambiente_financeiro_id, conta_bancaria_destino_id, 'CREDITO', valor, data_pagamento, descricao, grupo_transferencia_id
FROM tmp_transferencia_migrar;

DROP TABLE tmp_transferencia_migrar;

-- 3) Remove todos os Lancamentos TRANSFERENCIA (PAGO ja migrado acima;
--    PENDENTE/ATRASADO/CANCELADO nunca chegaram a mover dinheiro e o
--    conceito de Transferencia agendada nao existe mais - ADR-012).
DELETE FROM lancamento WHERE tipo = 'TRANSFERENCIA';
