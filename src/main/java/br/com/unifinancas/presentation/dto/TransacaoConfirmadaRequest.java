package br.com.unifinancas.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * SAD §7.2, RF-21, T4.1.4 - item de {@link ConfirmarImportacaoRequest}: a
 * transacao ja revisada pelo usuario (categoria escolhida, ou nenhuma).
 * {@code identificadorExterno} obrigatorio - e o resolvido devolvido por
 * {@code POST .../ofx} / {@code .../analisar}, ecoado de volta (nao
 * recalculado aqui).
 */
public record TransacaoConfirmadaRequest(
        @NotNull(message = "data e obrigatoria") LocalDate data,
        @NotNull(message = "valor e obrigatorio") BigDecimal valor,
        @NotBlank(message = "descricao e obrigatoria") String descricao,
        UUID categoriaId,
        @NotBlank(message = "identificadorExterno e obrigatorio") String identificadorExterno) {
}
