package br.com.unifinancas.domain.movimentacao;

/**
 * ADR-014, SAD Sec4.4a: torna explicito de onde a Movimentacao nasceu -
 * antes so inferido por {@code lancamentoId}/{@code grupoTransferenciaId}
 * preenchidos. Determina mutabilidade: so {@code MANUAL} pode ser editada
 * ou excluida livremente (Movimentacao.editar); LIQUIDACAO/TRANSFERENCIA sao
 * imutaveis - corrigir significa desfazer a origem (MovimentacaoService.excluir).
 */
public enum OrigemMovimentacao {
    /** Nasceu de {@code LancamentoService.confirmarPagamento}/{@code confirmarRecebimento} (RN-12). */
    LIQUIDACAO,
    /** Nasceu de uma Transferencia - sempre em par, mesmo {@code grupoTransferenciaId} (RN-05). */
    TRANSFERENCIA,
    /** ADR-014: registrada diretamente pelo usuario (tarifa, juros, ajuste de saldo), sem Lancamento nem Transferencia. */
    MANUAL
}
