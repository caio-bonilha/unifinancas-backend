package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Gap identificado pelo usuario - PUT /v1/ambientes/{id}. {@code tipo} nao e
 * editavel (ver {@code AmbienteFinanceiro.editar}); {@code
 * diasAntecedenciaNotificacaoVencimento} e opcional (ausente = 3 dias, mesma
 * regra de {@link CriarAmbienteRequest}).
 */
public record EditarAmbienteRequest(
        @NotBlank(message = "nome e obrigatorio") String nome,
        Integer diasAntecedenciaNotificacaoVencimento) {
}
