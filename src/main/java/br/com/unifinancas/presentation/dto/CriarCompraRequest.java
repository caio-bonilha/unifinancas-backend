package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * SAD §7.2, RF-18, §4.8 - POST /v1/compras. {@code contatoId}/
 * {@code formaPagamento}/{@code cartaoId}/{@code parcelas}/{@code documentoId}/
 * {@code descricao} sao opcionais; {@code cartaoId} so faz sentido quando
 * {@code formaPagamento = CARTAO_CREDITO} (validado em {@code Compra.novo}).
 */
public record CriarCompraRequest(
        UUID contatoId,
        @NotNull(message = "data e obrigatoria") LocalDate data,
        FormaPagamento formaPagamento,
        UUID cartaoId,
        Integer parcelas,
        UUID documentoId,
        String descricao,
        @NotEmpty(message = "itens e obrigatorio - uma Compra deve ter pelo menos um item") List<@Valid ItemCompraRequest> itens) {
}
