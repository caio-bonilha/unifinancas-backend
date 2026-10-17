-- Identidade e Ambiente Financeiro (SAD Sec5.1, Sec5.2). Primeiras tabelas
-- de dominio do sistema (Fase 1, Etapa 1.1).
CREATE TABLE usuario (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO' CHECK (status IN ('ATIVO', 'INATIVO', 'BLOQUEADO')),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL
);

CREATE TABLE ambiente_financeiro (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    nome VARCHAR(150) NOT NULL,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('PESSOAL', 'FAMILIAR', 'PROJETO')),
    plano VARCHAR(20) NOT NULL DEFAULT 'GRATUITO' CHECK (plano IN ('GRATUITO', 'PAGO')),
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO' CHECK (status IN ('ATIVO', 'SUSPENSO', 'ENCERRADO')),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL
);

CREATE TABLE usuario_ambiente_financeiro (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    usuario_id UUID NOT NULL REFERENCES usuario (id),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    perfil VARCHAR(20) NOT NULL CHECK (perfil IN ('OWNER', 'ADMIN', 'MEMBRO', 'VISUALIZADOR')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ATIVO', 'CONVITE_PENDENTE', 'REMOVIDO')),
    data_entrada TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_usuario_ambiente UNIQUE (usuario_id, ambiente_financeiro_id)
);

CREATE INDEX idx_uaf_usuario ON usuario_ambiente_financeiro (usuario_id);
CREATE INDEX idx_uaf_ambiente ON usuario_ambiente_financeiro (ambiente_financeiro_id);
