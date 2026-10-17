-- T3.1.12, ADR-020: exclusao de Documento em duas fases. Fase 1 (aqui):
-- soft-delete -- o Documento some das listagens no ato e a operacao e
-- reversivel ate a purga. Fase 2: PurgaDocumentoJob apaga em definitivo
-- (linha + objeto no Object Storage + processamento_documento 1:1) os
-- registros fora da janela de retencao (unifinancas.documentos.purga.retencao-dias).
--
-- Numerada V36 (nao V30, o proximo livre a partir de origin/desenvolvimento):
-- o branch local `desenvolvimento` ja vai ate V35 (compra/venda + T4.1.3), e
-- esta tarefa precisa cair depois dessas.

ALTER TABLE documento
    ADD COLUMN excluido_em TIMESTAMPTZ,
    ADD COLUMN excluido_por_usuario_id UUID REFERENCES usuario (id);

-- Indice parcial: so os soft-deletados interessam a varredura da purga.
CREATE INDEX idx_documento_excluido_em ON documento (excluido_em) WHERE excluido_em IS NOT NULL;
