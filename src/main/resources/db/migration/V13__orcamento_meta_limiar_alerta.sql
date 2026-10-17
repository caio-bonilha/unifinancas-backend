-- T1.5.3, Fluxo 6.4: limiar de alerta de Orcamento configuravel por
-- categoria/ambiente (decisao consultada com o usuario) - "estourado"
-- (>=100% do valor_limite) e fixo por definicao, so o limiar de "aviso" e
-- configuravel (default 80). ultimo_alerta_tipo/ultimo_alerta_periodo
-- evitam notificar repetidamente o mesmo limiar dentro do mesmo periodo.
ALTER TABLE orcamento_meta ADD COLUMN limiar_alerta_percentual NUMERIC(5, 2) NOT NULL DEFAULT 80;
ALTER TABLE orcamento_meta ADD COLUMN ultimo_alerta_tipo VARCHAR(20) CHECK (ultimo_alerta_tipo IN ('AVISO', 'ESTOURADO'));
ALTER TABLE orcamento_meta ADD COLUMN ultimo_alerta_periodo DATE;
