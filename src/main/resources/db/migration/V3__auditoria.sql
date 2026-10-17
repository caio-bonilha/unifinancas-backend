-- Trilha de auditoria (RNF-01, RNF-07 - SAD Sec5.2, Sec5.3).
-- ambiente_financeiro_id e usuario_id ainda NAO tem FK: as tabelas
-- ambiente_financeiro e usuario so serao criadas na T1.1.1 (Fase 1). Uma
-- migracao futura, apos a T1.1.1, deve adicionar essas duas constraints via
-- ALTER TABLE. Ate la os dois campos ficam como UUID solto (ja nullable no
-- SAD, exatamente para os casos sem tenant/usuario: login, acoes do sistema).
CREATE TABLE auditoria (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID,
    usuario_id UUID,
    entidade VARCHAR(50) NOT NULL,
    entidade_id UUID NOT NULL,
    acao VARCHAR(20) NOT NULL CHECK (acao IN ('CRIACAO', 'ALTERACAO', 'CANCELAMENTO', 'EXCLUSAO')),
    dados_antes JSONB,
    dados_depois JSONB,
    criado_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auditoria_entidade ON auditoria (entidade, entidade_id);
