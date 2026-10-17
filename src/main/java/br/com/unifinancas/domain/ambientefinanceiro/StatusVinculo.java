package br.com.unifinancas.domain.ambientefinanceiro;

/**
 * Ciclo de vida do vinculo {@code UsuarioAmbienteFinanceiro} (SAD Sec4.3). O
 * vinculo so passa a existir quando um convite (ver {@code Convite}) e
 * aceito ou quando alguem cria/e o primeiro membro de um ambiente - por
 * isso nao ha um status "pendente" aqui, o vinculo ja nasce ATIVO.
 */
public enum StatusVinculo {
    ATIVO,
    REMOVIDO
}
