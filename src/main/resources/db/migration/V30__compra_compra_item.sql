-- Compra e CompraItem (RF-18, SAD Sec4.8/Sec5.2). T2.2.1.
-- criado_em/atualizado_em em ambas as tabelas por Sec5.1 ("toda tabela
-- transacional tem criado_em, atualizado_em") - o catalogo da Sec5.2 nao
-- listava essas duas colunas para compra/compra_item (unico par de tabelas
-- do catalogo sem essa linha, diferente de cartao/fatura); tratado como
-- lacuna de documentacao, nao decisao deliberada - corrigido no SAD junto
-- com esta tarefa.
CREATE TABLE compra (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    contato_id UUID REFERENCES contato (id), -- estabelecimento/fornecedor, opcional
    data DATE NOT NULL,
    valor_total NUMERIC(15, 2) NOT NULL,
    forma_pagamento VARCHAR(20) CHECK (forma_pagamento IN ('DINHEIRO', 'DEBITO', 'CARTAO_CREDITO', 'PIX', 'BOLETO', 'TRANSFERENCIA')),
    cartao_id UUID REFERENCES cartao (id), -- so preenchido quando forma_pagamento = CARTAO_CREDITO
    parcelas SMALLINT NOT NULL DEFAULT 1 CHECK (parcelas >= 1),
    documento_id UUID REFERENCES documento (id), -- origem, ex.: cupom fiscal (RF-20)
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_compra_ambiente ON compra (ambiente_financeiro_id);

CREATE TABLE compra_item (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    compra_id UUID NOT NULL REFERENCES compra (id),
    produto_nome VARCHAR(200) NOT NULL,
    quantidade NUMERIC(10, 3) NOT NULL CHECK (quantidade > 0),
    valor_unitario NUMERIC(15, 2) NOT NULL CHECK (valor_unitario > 0),
    valor_total NUMERIC(15, 2) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_compra_item_compra ON compra_item (compra_id);

-- FK adiada desde a T1.3.1 (V10) - compra agora existe (decisao de sequenciamento ja registrada la).
ALTER TABLE lancamento ADD CONSTRAINT fk_lancamento_compra FOREIGN KEY (compra_id) REFERENCES compra (id);
