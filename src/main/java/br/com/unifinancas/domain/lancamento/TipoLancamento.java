package br.com.unifinancas.domain.lancamento;

/**
 * SAD Sec4.7. [Revisado - ADR-012] TRANSFERENCIA removida: transferencia
 * entre contas deixou de ser um Lancamento e passou a ser a criacao atomica
 * de duas Movimentacoes de Conta Bancaria (RN-05, SAD Sec4.4a).
 */
public enum TipoLancamento {
    RECEITA,
    DESPESA
}
