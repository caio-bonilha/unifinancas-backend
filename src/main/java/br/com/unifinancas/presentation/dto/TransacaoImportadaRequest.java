package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SAD §7.2, RF-21, T4.1.4 - item de {@link AnalisarImportacaoRequest}, já
 * mapeado (CSV/Excel: pelo usuário na revisão, a partir da
 * {@link TabelaImportadaResponse}; OFX não passa por aqui - {@code POST
 * .../ofx} já devolve analisado). {@code valor} assinado (negativo =
 * despesa, positivo = receita); {@code identificadorExterno} opcional (o
 * FITID quando vier do OFX; ausente para CSV/Excel - {@code ImportacaoService}
 * resolve um sintético).
 */
public record TransacaoImportadaRequest(
        @NotNull(message = "data e obrigatoria") LocalDate data,
        @NotNull(message = "valor e obrigatorio") BigDecimal valor,
        @NotBlank(message = "descricao e obrigatoria") String descricao,
        String identificadorExterno) {
}
