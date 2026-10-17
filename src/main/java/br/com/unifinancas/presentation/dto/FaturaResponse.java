package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.fatura.StatusFatura;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** SAD §7.2, §4.6. */
public record FaturaResponse(
        UUID id, UUID cartaoId, LocalDate competencia, LocalDate dataFechamento, LocalDate dataVencimento,
        BigDecimal valorTotal, BigDecimal valorPago, StatusFatura status) {
}
