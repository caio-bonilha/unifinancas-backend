package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.contabancaria.TipoContaBancaria;
import jakarta.validation.constraints.NotNull;

/**
 * T1.9.6 - PATCH /v1/contas-bancarias/{id}. {@code nome}/{@code tipo} so sao
 * obrigatorios quando {@code acao=EDITAR} (validado no controller, mesmo
 * padrao de {@code AtualizarLancamentoRequest}).
 */
public record AtualizarContaBancariaRequest(
        @NotNull(message = "acao e obrigatoria") AcaoContaBancaria acao, String nome, TipoContaBancaria tipo) {
}
