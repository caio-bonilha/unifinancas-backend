-- Suporte tecnico ao processamento assincrono (SAD Sec5.2, Sec3.4, ADR-005).
-- Nao e uma entidade de dominio, e infraestrutura - por isso nao carrega
-- ambiente_financeiro_id como coluna: o tenant relevante (quando houver)
-- vai dentro do payload (SAD Sec12.6).
CREATE TABLE job_execucao (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    tipo VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('AGENDADO', 'EM_EXECUCAO', 'CONCLUIDO', 'ERRO')),
    tentativas SMALLINT NOT NULL DEFAULT 0,
    agendado_para TIMESTAMPTZ NOT NULL,
    executado_em TIMESTAMPTZ,
    erro_mensagem TEXT
);

CREATE INDEX idx_job_status_agendado ON job_execucao (status, agendado_para);
