package br.com.unifinancas.infrastructure.importacao;

import java.time.LocalDate;
import java.util.List;

/**
 * Extrato bancario extraido de um arquivo OFX (RF-21, T4.1.1) - metadados da
 * conta/periodo (`BANKACCTFROM`/`CCACCTFROM`, `DTSTART`/`DTEND`) mais a lista
 * de transacoes (`STMTTRN`, dentro de `BANKTRANLIST`).
 */
public record ExtratoOfx(String instituicaoId, String numeroConta, String moeda, LocalDate dataInicio, LocalDate dataFim,
        List<TransacaoOfx> transacoes) {
}
