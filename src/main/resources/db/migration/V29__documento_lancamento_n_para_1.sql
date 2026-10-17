-- Etapa B (pedido do usuario): o vinculo Documento <-> Lancamento passa de
-- 1:1 (lancamento.documento_id) para N:1 -- um Lancamento pode ter varios
-- Documentos (boleto + comprovante de pagamento + nota...), cada Documento
-- pertence a no maximo um Lancamento. O lado "dono" da FK inverte: agora
-- mora em documento.lancamento_id.
--
-- Ver SAD Sec4.7, Sec4.14, Sec5.2 e o ADR "Documento N:1 Lancamento".

ALTER TABLE documento
    ADD COLUMN lancamento_id UUID REFERENCES lancamento (id);

CREATE INDEX idx_documento_lancamento ON documento (lancamento_id);

-- Backfill: traz para o novo lado todo vinculo que hoje mora em
-- lancamento.documento_id (cobre tanto os anexados na T1.8.3 quanto os
-- gerados na confirmacao de documento processado, T3.1.5).
UPDATE documento d
   SET lancamento_id = l.id
  FROM lancamento l
 WHERE l.documento_id = d.id;

-- lancamento.documento_id deixa de existir; a FK criada na V18
-- (lancamento_documento_id_fkey) cai junto com a coluna.
ALTER TABLE lancamento DROP COLUMN documento_id;
