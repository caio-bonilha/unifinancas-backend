-- Etapa A (pedido do usuário): o Lançamento passa a guardar a linha
-- digitável do boleto/conta (47 dígitos de cobrança bancária ou 48 de
-- arrecadação). Armazenada só com dígitos (sem pontos/espaços); a
-- formatação e o código de barras são reconstruídos na exibição.
ALTER TABLE lancamento
    ADD COLUMN linha_digitavel VARCHAR(48);
