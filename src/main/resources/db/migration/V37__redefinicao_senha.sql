-- T1.1.10 (SAD Sec5.2, Sec6.7, Sec8.1, RN-14): token de "esqueci minha senha".
-- Pertence a identidade global (Usuario), nao a um Ambiente Financeiro - por
-- isso NAO tem ambiente_financeiro_id (mesma natureza de refresh_token e do
-- lado "e-mail sem conta" de convite). Guarda so o hash SHA-256 do token,
-- nunca o valor bruto (Sec8.4). Uso unico (usado_em) e expiracao curta
-- (unifinancas.auth.redefinicao-senha.validade, default 1h). Consumir o token
-- revoga todos os refresh tokens do Usuario (RN-14) - feito na aplicacao,
-- nao aqui.
CREATE TABLE redefinicao_senha (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    usuario_id UUID NOT NULL REFERENCES usuario (id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_em TIMESTAMPTZ NOT NULL,
    usado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_redefinicao_senha_usuario ON redefinicao_senha (usuario_id);
