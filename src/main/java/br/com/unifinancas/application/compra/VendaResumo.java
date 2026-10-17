package br.com.unifinancas.application.compra;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** T2.3.2, SAD Sec4.9. */
public record VendaResumo(UUID id, UUID contatoId, LocalDate data, BigDecimal valorTotal, FormaPagamento formaPagamento,
        UUID documentoId, String descricao, List<VendaItemResumo> itens) {
}
