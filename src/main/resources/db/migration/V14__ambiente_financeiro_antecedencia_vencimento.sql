-- T1.5.4, SAD Sec6.5: quantidade de dias de antecedencia para notificar um
-- Lancamento proximo do vencimento, configuravel por Ambiente Financeiro
-- (decisao consultada com o usuario - o SAD nao propunha um valor fixo nem
-- configuravel, so cita a notificacao de vencimento em RF-14). Default 3.
ALTER TABLE ambiente_financeiro
    ADD COLUMN dias_antecedencia_notificacao_vencimento INTEGER NOT NULL DEFAULT 3;
