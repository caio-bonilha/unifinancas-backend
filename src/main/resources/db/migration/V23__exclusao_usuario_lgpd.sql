-- T1.11.4, RNF-09 (LGPD - portabilidade e direito ao esquecimento): Usuario
-- ganha o status EXCLUIDO (soft-delete + anonimizacao, RN-13) - o valor ja
-- era previsto no ciclo de vida documentado no SAD (Sec4.1) mas nunca tinha
-- sido adicionado ao CHECK da coluna.
ALTER TABLE usuario DROP CONSTRAINT usuario_status_check;
ALTER TABLE usuario ADD CONSTRAINT usuario_status_check CHECK (status IN ('ATIVO', 'INATIVO', 'BLOQUEADO', 'EXCLUIDO'));
