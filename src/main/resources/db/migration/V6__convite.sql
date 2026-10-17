-- Convite de membro para um Ambiente Financeiro (RF-04, SAD Sec12.3/Sec12.5).
-- Cobre os dois casos descritos no Sec12.5: convite para um e-mail que ja
-- tem Usuario (usuario_id preenchido no convite) e para um e-mail sem conta
-- ainda (usuario_id nulo ate a ativacao criar o Usuario). O vinculo em
-- usuario_ambiente_financeiro so passa a existir quando o convite e aceito -
-- nao ha mais um status CONVITE_PENDENTE la (ver migracao anterior e a
-- entidade UsuarioAmbienteFinanceiro).
CREATE TABLE convite (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    email VARCHAR(255) NOT NULL,
    usuario_id UUID REFERENCES usuario (id),
    perfil VARCHAR(20) NOT NULL CHECK (perfil IN ('OWNER', 'ADMIN', 'MEMBRO', 'VISUALIZADOR')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDENTE', 'ACEITO')),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_convite_ambiente ON convite (ambiente_financeiro_id);
CREATE INDEX idx_convite_email ON convite (email);
