package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** SAD §7.2, §4.9. */
public record VendaResponse(UUID id, UUID contatoId, LocalDate data, BigDecimal valorTotal, FormaPagamento formaPagamento,
        UUID documentoId, String descricao, List<VendaItemResponse> itens) {
}
