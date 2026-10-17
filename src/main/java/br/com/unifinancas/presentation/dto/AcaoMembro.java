package br.com.unifinancas.presentation.dto;

/** SAD §7.2 - PATCH /v1/ambientes/{id}/membros/{usuarioId}: uma unica rota cobre as duas acoes (Sec12.3). */
public enum AcaoMembro {
    ALTERAR_PERFIL,
    REMOVER
}
