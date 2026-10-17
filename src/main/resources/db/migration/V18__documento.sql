-- Documento: arquivo anexado ou usado como fonte de dados (boleto, cupom
-- fiscal, nota fiscal, comprovante) - RF-16, SAD Sec4.14. Apenas a tabela
-- nesta tarefa (T1.8.1); sem processamento_documento ainda - isso e Fase 3
-- (SAD Sec4.15).
--
-- arquivo_url guarda a chave/caminho do objeto no Object Storage (T1.8.2),
-- nao uma URL publica permanente - o bucket nao tem acesso publico direto
-- (Sec8.4); URLs assinadas de curta duracao sao geradas sob demanda a
-- partir dessa chave.
CREATE TABLE documento (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('BOLETO', 'CUPOM_FISCAL', 'NOTA_FISCAL', 'COMPROVANTE')),
    arquivo_url VARCHAR(500) NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    tamanho_bytes BIGINT NOT NULL CHECK (tamanho_bytes > 0),
    mime_type VARCHAR(100) NOT NULL,
    enviado_por_usuario_id UUID NOT NULL REFERENCES usuario (id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_documento_ambiente ON documento (ambiente_financeiro_id);

-- FK adiada desde a T1.3.1 (lancamento.documento_id nasceu sem constraint,
-- pois esta tabela ainda nao existia) - adicionada agora que documento
-- existe, conforme o comentario da propria migracao V10 instrui.
ALTER TABLE lancamento
    ADD CONSTRAINT lancamento_documento_id_fkey
    FOREIGN KEY (documento_id) REFERENCES documento (id);
