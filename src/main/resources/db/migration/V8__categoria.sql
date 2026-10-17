-- Categoria: classificacao hierarquica das movimentacoes (RF-09, SAD Sec4.10).
-- categoria_pai_id e auto-relacionamento opcional - raiz da arvore quando nulo.
CREATE TABLE categoria (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    nome VARCHAR(100) NOT NULL,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('RECEITA', 'DESPESA', 'AMBOS')),
    categoria_pai_id UUID REFERENCES categoria (id),
    ativa BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_categoria_ambiente ON categoria (ambiente_financeiro_id);
