package br.com.unifinancas.application.fatura;

import br.com.unifinancas.domain.fatura.StatusFatura;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FaturaResumo(
        UUID id, UUID cartaoId, LocalDate competencia, LocalDate dataFechamento, LocalDate dataVencimento,
        BigDecimal valorTotal, BigDecimal valorPago, StatusFatura status) {
}
