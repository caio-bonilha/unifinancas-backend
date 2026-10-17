-- Lancamento Financeiro: nucleo transacional do sistema (RF-06 a RF-08,
-- SAD Sec4.7/Sec5.2). Somente a tabela nesta tarefa (T1.3.1) - entidade de
-- dominio e regras (RN-05, RN-06) ficam para a T1.3.2.
--
-- Decisao de sequenciamento (consultada com o usuario nesta tarefa): o SAD
-- Sec5.2 lista cartao_id, fatura_id, lancamento_recorrente_id, compra_id,
-- venda_id e documento_id como FK, mas nenhuma dessas tabelas existe ainda
-- (cartao/fatura e compra/venda sao Fase 2; lancamento_recorrente e
-- documento sao tarefas posteriores desta mesma Fase 1 - T1.6.1 e T1.8.1).
-- Essas seis colunas nascem UUID nullable SEM constraint de FK; cada tarefa
-- que criar a tabela referenciada (T1.6.1, T1.8.1, T2.1.1, T2.2.1, T2.3.1)
-- deve adicionar o respectivo ALTER TABLE lancamento ADD CONSTRAINT ...
-- FOREIGN KEY nessa ocasiao.
CREATE TABLE lancamento (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('RECEITA', 'DESPESA', 'TRANSFERENCIA')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDENTE', 'PAGO', 'RECEBIDO', 'CANCELADO', 'ATRASADO')),
    valor NUMERIC(15, 2) NOT NULL CHECK (valor > 0),
    data_emissao DATE NOT NULL,
    data_vencimento DATE NOT NULL,
    data_pagamento DATE,
    categoria_id UUID REFERENCES categoria (id),
    contato_id UUID REFERENCES contato (id),
    conta_bancaria_id UUID REFERENCES conta_bancaria (id),
    conta_bancaria_destino_id UUID REFERENCES conta_bancaria (id), -- apenas para TRANSFERENCIA
    forma_pagamento VARCHAR(20) CHECK (forma_pagamento IN ('DINHEIRO', 'DEBITO', 'CARTAO_CREDITO', 'PIX', 'BOLETO', 'TRANSFERENCIA')),
    cartao_id UUID, -- FK para cartao(id) adicionada na T2.1.1, quando a tabela existir
    fatura_id UUID, -- FK para fatura(id) adicionada na T2.1.1
    origem VARCHAR(20) NOT NULL CHECK (origem IN ('MANUAL', 'COMPRA', 'VENDA', 'RECORRENCIA', 'IMPORTACAO', 'DOCUMENTO')),
    lancamento_recorrente_id UUID, -- FK para lancamento_recorrente(id) adicionada na T1.6.1
    compra_id UUID, -- FK para compra(id) adicionada na T2.2.1
    venda_id UUID, -- FK para venda(id) adicionada na T2.3.1
    documento_id UUID, -- FK para documento(id) adicionada na T1.8.1
    observacao TEXT,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Sustenta contas a pagar/receber (RF-07, RF-08) e notificacoes de vencimento (Etapa 1.5).
CREATE INDEX idx_lancamento_ambiente_status_venc ON lancamento (ambiente_financeiro_id, status, data_vencimento);

-- Sustenta o calculo de progresso de Orcamento/Meta (RN-10, Etapa 1.4).
CREATE INDEX idx_lancamento_categoria_periodo ON lancamento (categoria_id, data_vencimento);
