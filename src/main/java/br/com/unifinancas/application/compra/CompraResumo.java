package br.com.unifinancas.application.compra;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** T2.2.2, SAD Sec4.8. */
public record CompraResumo(UUID id, UUID contatoId, LocalDate data, BigDecimal valorTotal, FormaPagamento formaPagamento,
        UUID cartaoId, int parcelas, UUID documentoId, String descricao, List<CompraItemResumo> itens) {
}
