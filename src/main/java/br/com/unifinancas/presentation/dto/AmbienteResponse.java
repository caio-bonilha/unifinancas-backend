package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.TipoAmbienteFinanceiro;
import java.util.UUID;

/** SAD §7.2, RF-03. */
public record AmbienteResponse(UUID id, String nome, TipoAmbienteFinanceiro tipo, PerfilUsuarioAmbiente perfil,
        int diasAntecedenciaNotificacaoVencimento) {
}
