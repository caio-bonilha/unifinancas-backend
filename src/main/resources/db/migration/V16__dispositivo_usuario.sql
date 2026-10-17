-- T1.5.6, SAD Sec7.6: registro de token de dispositivo (Firebase Cloud
-- Messaging) para o canal PUSH ter um destinatario real - nao existia no
-- SAD original, adicionado junto com o adapter de push/e-mail (decisao
-- consultada com o usuario). Sem ambiente_financeiro_id: o dispositivo
-- pertence ao Usuario (identidade global), nao a um Ambiente Financeiro
-- especifico - mesmo raciocinio de usuario/refresh_token.
CREATE TABLE dispositivo_usuario (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    usuario_id UUID NOT NULL REFERENCES usuario (id),
    token VARCHAR(255) NOT NULL,
    plataforma VARCHAR(10) NOT NULL CHECK (plataforma IN ('ANDROID', 'IOS')),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_dispositivo_token UNIQUE (token)
);

CREATE INDEX idx_dispositivo_usuario ON dispositivo_usuario (usuario_id);
