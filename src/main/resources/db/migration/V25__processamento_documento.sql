-- T3.1.2, SAD Sec4.15/Sec5.2, ADR-016: registro do pipeline de OCR/IA sobre
-- um Documento (Fase 3). So a tabela e a entidade nesta tarefa - o adapter da
-- cascata (XML -> PDFBox -> Tesseract -> OpenAI) e T3.1.3, o worker que a
-- movimenta e T3.1.4 (Fluxo 6.3).

-- ADR-016: opt-in por Ambiente Financeiro para o ultimo recurso da cascata de
-- extracao (envio do documento a OpenAI API quando XML/PDFBox/OCR proprio
-- falham). Default FALSE - o dado financeiro so sai para terceiro com
-- consentimento explicito do tenant. So ter a chave OPENAI_API_KEY no backend
-- nao basta; esta flag tambem precisa estar ligada.
ALTER TABLE ambiente_financeiro
    ADD COLUMN ocr_fallback_ia_habilitado BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE processamento_documento (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    -- SAD Sec5.2 nao lista ambiente_financeiro_id (isolamento seria so via a FK
    -- unica para documento). Incluido aqui pela regra nao-negociavel #3 do
    -- projeto e pelo padrao de toda entidade de dominio do projeto - job_execucao
    -- e a unica tabela sem a coluna, e e infraestrutura, nao dominio. Denormalizado
    -- do documento pai; permite consulta escopada por tenant sem join (decisao
    -- consultada com o usuario, SAD Sec5.2 atualizado).
    ambiente_financeiro_id UUID NOT NULL REFERENCES ambiente_financeiro (id),
    documento_id UUID NOT NULL UNIQUE REFERENCES documento (id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDENTE', 'PROCESSANDO', 'AGUARDANDO_REVISAO', 'CONFIRMADO', 'ERRO')),
    dados_extraidos JSONB,
    erro_mensagem TEXT,
    tentativas SMALLINT NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
    lancamento_gerado_id UUID REFERENCES lancamento (id),
    -- compra_gerada_id nasce SEM constraint de FK: a tabela `compra` ainda nao
    -- existe (Fase 2, T2.2.1) e esta tarefa esta sendo feita fora da ordem do
    -- roadmap. A FK `processamento_documento_compra_gerada_id_fkey` deve ser
    -- adicionada na migracao que criar `compra`, mesmo padrao do que a V18 fez
    -- para lancamento.documento_id (que a V10 deixou sem constraint).
    compra_gerada_id UUID,
    iniciado_em TIMESTAMPTZ,
    concluido_em TIMESTAMPTZ
);

-- SAD Sec5.2: usado pelo worker assincrono (T3.1.4) para varrer o que esta
-- pendente/em erro. Escopado por ambiente para as consultas da API (T3.1.5+).
CREATE INDEX idx_processamento_status ON processamento_documento (status);
CREATE INDEX idx_processamento_ambiente ON processamento_documento (ambiente_financeiro_id);
