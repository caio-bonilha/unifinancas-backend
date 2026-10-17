package br.com.unifinancas.application.relatorio;

import java.math.BigDecimal;

/** T1.7.1, RF-15: contas a pagar/receber ainda PENDENTE ou ATRASADO, sem filtro de periodo (visao geral do que esta em aberto agora). */
public record ResumoVencimentos(
        long quantidadeContasAPagar, BigDecimal valorContasAPagar,
        long quantidadeContasAReceber, BigDecimal valorContasAReceber) {
}
