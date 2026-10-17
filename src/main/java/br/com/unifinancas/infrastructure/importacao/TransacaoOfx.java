package br.com.unifinancas.infrastructure.importacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma transacao (`STMTTRN`) extraida de um arquivo OFX (RF-21, T4.1.1).
 * {@code identificadorExterno} e o `FITID` do banco - unico dentro da conta
 * de origem, usado pela deteccao de duplicidade (T4.1.3, fora de escopo
 * desta tarefa). {@code descricao} combina `NAME`/`MEMO` (o que existir;
 * ambos quando os dois vierem preenchidos).
 */
public record TransacaoOfx(String identificadorExterno, LocalDate data, BigDecimal valor, TipoTransacaoOfx tipo, String descricao) {
}
