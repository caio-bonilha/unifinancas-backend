-- T2.3.2 (mesma decisao ja tomada para Compra em V31/T2.2.2, aplicada ao par
-- simetrico): Venda ganha uma descricao opcional, usada como descricao do
-- Lancamento gerado por CompraVendaService quando informada; ausente, a
-- descricao e derivada dos itens (nome do primeiro item, "e mais N itens").
ALTER TABLE venda ADD COLUMN descricao VARCHAR(200);
