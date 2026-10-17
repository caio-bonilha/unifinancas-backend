-- Gap identificado pelo usuario: o Lancamento precisa de uma descricao curta
-- (ex.: "Uber para o aeroporto"), distinta da Observacao (nota livre,
-- opcional, ja existente) - a descricao vira o texto principal nas listas.
-- Obrigatoria (CHECK <> ''); linhas existentes recebem um valor de backfill
-- generico e o DEFAULT e removido em seguida, para que novos INSERTs
-- precisem informar a descricao explicitamente (mesmo padrao de `valor`).
ALTER TABLE lancamento ADD COLUMN descricao VARCHAR(200) NOT NULL DEFAULT 'Sem descrição' CHECK (descricao <> '');
ALTER TABLE lancamento ALTER COLUMN descricao DROP DEFAULT;
