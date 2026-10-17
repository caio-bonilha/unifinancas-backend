-- Venda e VendaItem (RF-19, SAD Sec4.9/Sec5.2). T2.3.1. Estrutura simetrica
-- a compra/compra_item (V30/V31) - sem cartao_id/parcelas (Sec5.2: "nao faz
-- sentido receber por cartao de credito", so quem compra parcela, nao quem
-- vende) e sem descricao ainda (mesmo sequenciamento incremental de compra:
-- so ganhou descricao quando a geracao de Lancamento, T2.2.2, precisou -
-- aqui a decisao correspondente fica para T2.3.2).
CREATE TABLE venda (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    contato_id UUID REFERENCES contato (id), -- cliente, opcional
    data DATE NOT NULL,
    valor_total NUMERIC(15, 2) NOT NULL,
    forma_pagamento VARCHAR(20) CHECK (forma_pagamento IN ('DINHEIRO', 'DEBITO', 'CARTAO_CREDITO', 'PIX', 'BOLETO', 'TRANSFERENCIA')),
    documento_id UUID REFERENCES documento (id), -- origem, ex.: nota fiscal emitida
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_venda_ambiente ON venda (ambiente_financeiro_id);

CREATE TABLE venda_item (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    venda_id UUID NOT NULL REFERENCES venda (id),
    produto_nome VARCHAR(200) NOT NULL,
    quantidade NUMERIC(10, 3) NOT NULL CHECK (quantidade > 0),
    valor_unitario NUMERIC(15, 2) NOT NULL CHECK (valor_unitario > 0),
    valor_total NUMERIC(15, 2) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_venda_item_venda ON venda_item (venda_id);

-- FK adiada desde a T1.3.1 (V10) - venda agora existe (decisao de sequenciamento ja registrada la).
ALTER TABLE lancamento ADD CONSTRAINT fk_lancamento_venda FOREIGN KEY (venda_id) REFERENCES venda (id);
