package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** SAD §7.2, §4.8. */
public record CompraResponse(UUID id, UUID contatoId, LocalDate data, BigDecimal valorTotal, FormaPagamento formaPagamento,
        UUID cartaoId, int parcelas, UUID documentoId, String descricao, List<CompraItemResponse> itens) {
}
