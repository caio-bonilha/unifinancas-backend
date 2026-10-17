package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * SAD §7.2, RF-19, §4.9 - POST /v1/vendas. {@code contatoId}/
 * {@code formaPagamento}/{@code documentoId}/{@code descricao} sao opcionais.
 * Sem {@code cartaoId}/{@code parcelas} (Sec5.2: quem parcela e quem compra,
 * nao quem vende).
 */
public record CriarVendaRequest(
        UUID contatoId,
        @NotNull(message = "data e obrigatoria") LocalDate data,
        FormaPagamento formaPagamento,
        UUID documentoId,
        String descricao,
        @NotEmpty(message = "itens e obrigatorio - uma Venda deve ter pelo menos um item") List<@Valid ItemVendaRequest> itens) {
}
