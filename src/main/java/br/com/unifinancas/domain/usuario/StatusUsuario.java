package br.com.unifinancas.domain.usuario;

/** Ciclo de vida do {@link Usuario} (SAD Sec4.1). EXCLUIDO e terminal (RN-13, T1.11.4, RNF-09/LGPD) - nunca volta a ATIVO. */
public enum StatusUsuario {
    ATIVO,
    INATIVO,
    BLOQUEADO,
    EXCLUIDO
}
