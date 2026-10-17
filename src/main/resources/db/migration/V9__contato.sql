-- Contato: cliente/fornecedor associado a operacoes financeiras (RF-10, SAD Sec4.12).
CREATE TABLE contato (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    nome VARCHAR(150) NOT NULL,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('CLIENTE', 'FORNECEDOR', 'AMBOS')),
    documento VARCHAR(20),
    email VARCHAR(255),
    telefone VARCHAR(20),
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_contato_ambiente ON contato (ambiente_financeiro_id);
