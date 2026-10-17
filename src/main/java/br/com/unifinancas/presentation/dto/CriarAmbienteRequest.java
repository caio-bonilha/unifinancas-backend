package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.ambientefinanceiro.TipoAmbienteFinanceiro;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * SAD §7.2 - POST /v1/ambientes. {@code diasAntecedenciaNotificacaoVencimento}
 * e opcional (ausente = 3 dias, T1.5.4).
 */
public record CriarAmbienteRequest(
        @NotBlank(message = "nome e obrigatorio") String nome,
        @NotNull(message = "tipo e obrigatorio") TipoAmbienteFinanceiro tipo,
        Integer diasAntecedenciaNotificacaoVencimento) {
}
