-- T4.1.3, RF-21, RNF-02 (decisao consultada com o usuario): deteccao de
-- duplicidade entre importacoes separadas (ex.: reimportar o mesmo OFX)
-- precisa de um identificador de origem persistido no Lancamento - FITID do
-- OFX, ou um hash de contaBancariaId+data+valor+descricao normalizada
-- quando a origem (CSV/Excel) nao tem identificador proprio.
-- Indice parcial (so linhas com identificador_externo preenchido, a
-- imensa maioria dos Lancamentos nunca tera esse campo) escopado por
-- conta_bancaria_id - FITID e unico por conta, nao globalmente.
ALTER TABLE lancamento ADD COLUMN identificador_externo VARCHAR(100);

CREATE INDEX idx_lancamento_identificador_externo ON lancamento (conta_bancaria_id, identificador_externo)
    WHERE identificador_externo IS NOT NULL;
