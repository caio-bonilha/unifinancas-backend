package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SAD §7.2, RN-11, ADR-017 - PATCH /v1/cartoes/{id}/faturas/{faturaId}.
 * {@code valor}/{@code dataPagamento} sao exigidos so para {@code acao=PAGAR};
 * {@code numeroParcelas} so para {@code acao=PARCELAR} (validado no
 * controller, mesmo padrao das demais rotas PATCH com `acao`).
 */
public record AtualizarFaturaRequest(
        @NotNull(message = "acao e obrigatoria") AcaoFatura acao,
        BigDecimal valor, LocalDate dataPagamento, Integer numeroParcelas) {
}
