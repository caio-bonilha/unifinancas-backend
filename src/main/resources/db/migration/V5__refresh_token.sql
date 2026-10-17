-- Suporte tecnico a autenticacao JWT (SAD Sec7.4, Sec8.1, ADR-010) - nao e
-- uma tabela do modelo de dominio do SAD (nao consta no catalogo da Sec5.2),
-- e a persistencia necessaria para tornar o refresh token "revogavel...no
-- banco" (ADR-010) e "rotativo": cada uso de um refresh token o revoga e
-- gera outro na mesma familia; se um token ja revogado for reapresentado
-- (sinal de furto), a familia inteira e revogada (forca novo login).
-- Guarda so o hash do token, nunca o valor bruto (mesmo espirito de nunca
-- persistir segredo em texto claro - Sec8.4).
CREATE TABLE refresh_token (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    usuario_id UUID NOT NULL REFERENCES usuario (id),
    familia_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    revogado BOOLEAN NOT NULL DEFAULT FALSE,
    expira_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_token_usuario ON refresh_token (usuario_id);
CREATE INDEX idx_refresh_token_familia ON refresh_token (familia_id);
