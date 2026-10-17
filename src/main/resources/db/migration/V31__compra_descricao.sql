-- T2.2.2 (decisao consultada com o usuario): Compra ganha uma descricao
-- opcional, usada como descricao do(s) Lancamento(s) gerados por
-- CompraVendaService quando informada; ausente, a descricao e derivada dos
-- itens da propria Compra (nome do primeiro item, "e mais N itens").
ALTER TABLE compra ADD COLUMN descricao VARCHAR(200);
