-- Lancamento Recorrente: regra que origina Lancamentos futuros (RF-11, SAD
-- Sec4.13). Apenas a tabela nesta tarefa (T1.6.1) - repositorio e endpoints
-- CRUD ficam para a T1.6.2, RecorrenciaJob para a T1.6.3, conforme o
-- roadmap sequencia (mesmo padrao de T1.3.1/T1.3.2/T1.3.3 para Lancamento).
--
-- conta_bancaria_destino_id: decisao consultada com o usuario nesta tarefa
-- (nao prevista no atributo original do SAD Sec4.13, que so lista
-- conta_bancaria_id) - RECEITA/DESPESA/TRANSFERENCIA sao suportados,
-- TRANSFERENCIA exigindo origem e destino distintos (mesma RN-05 de
-- Lancamento Financeiro).
CREATE TABLE lancamento_recorrente (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    descricao VARCHAR(150) NOT NULL,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('RECEITA', 'DESPESA', 'TRANSFERENCIA')),
    valor NUMERIC(15, 2) NOT NULL CHECK (valor > 0),
    categoria_id UUID REFERENCES categoria (id),
    conta_bancaria_id UUID REFERENCES conta_bancaria (id),
    conta_bancaria_destino_id UUID REFERENCES conta_bancaria (id), -- apenas para TRANSFERENCIA
    frequencia VARCHAR(10) NOT NULL CHECK (frequencia IN ('DIARIA', 'SEMANAL', 'MENSAL', 'ANUAL')),
    dia_referencia INTEGER, -- semantica exata (dia do mes/semana/ano) definida na T1.6.3, quando o calculo da proxima ocorrencia for implementado
    data_inicio DATE NOT NULL,
    data_fim DATE, -- nulo = recorrencia sem termino definido
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_lancamento_recorrente_ambiente_ativo ON lancamento_recorrente (ambiente_financeiro_id, ativo);

-- FK adiada desde a T1.3.1 (lancamento.lancamento_recorrente_id nasceu sem
-- constraint, pois esta tabela ainda nao existia) - adicionada agora que
-- lancamento_recorrente existe, conforme o comentario da propria migracao
-- V10 instrui.
ALTER TABLE lancamento
    ADD CONSTRAINT lancamento_lancamento_recorrente_id_fkey
    FOREIGN KEY (lancamento_recorrente_id) REFERENCES lancamento_recorrente (id);
