package br.com.unifinancas.application.ambientefinanceiro;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.StatusVinculo;
import java.util.UUID;

public record MembroResumo(
        UUID usuarioId, String nome, String email, PerfilUsuarioAmbiente perfil, StatusVinculo status) {
}
