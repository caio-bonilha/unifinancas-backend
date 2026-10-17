package br.com.unifinancas.presentation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * SAD §7.2, RF-21, T4.1.3/T4.1.4: uma transacao ja analisada (duplicidade +
 * sugestao de categoria) - {@code identificadorExterno} e o resolvido
 * (o FITID, ou o hash sintetico) - o frontend reenvia ele mesmo, sem
 * recalcular, ao confirmar ({@link TransacaoConfirmadaRequest}).
 */
public record TransacaoAnalisadaResponse(LocalDate data, BigDecimal valor, String descricao, String identificadorExterno,
        boolean duplicada, UUID lancamentoDuplicadoId, UUID categoriaSugeridaId) {
}
