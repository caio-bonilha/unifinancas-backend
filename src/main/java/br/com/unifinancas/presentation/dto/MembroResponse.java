package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.StatusVinculo;
import java.util.UUID;

public record MembroResponse(
        UUID usuarioId, String nome, String email, PerfilUsuarioAmbiente perfil, StatusVinculo status) {
}
