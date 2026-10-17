package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * SAD §7.2 - PATCH /v1/cartoes/{id}. Os demais campos so sao obrigatorios
 * quando {@code acao=EDITAR} (validado no controller, mesmo padrao de
 * {@code AtualizarContaBancariaRequest}).
 */
public record AtualizarCartaoRequest(
        @NotNull(message = "acao e obrigatoria") AcaoCartao acao,
        UUID contaBancariaId, String nome, BigDecimal limite, Integer diaFechamento, Integer diaVencimento,
        BigDecimal taxaJurosRotativoMensal) {
}
