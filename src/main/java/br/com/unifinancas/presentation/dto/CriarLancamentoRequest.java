package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** SAD §7.2 - POST /v1/lancamentos (RF-06, Fluxo 6.2). */
public record CriarLancamentoRequest(
        @NotNull(message = "tipo e obrigatorio") TipoLancamento tipo,
        @NotNull(message = "valor e obrigatorio") BigDecimal valor,
        @NotNull(message = "dataEmissao e obrigatoria") LocalDate dataEmissao,
        @NotNull(message = "dataVencimento e obrigatoria") LocalDate dataVencimento,
        UUID categoriaId,
        UUID contatoId,
        UUID contaBancariaId,
        FormaPagamento formaPagamento,
        @NotBlank(message = "descricao e obrigatoria") String descricao,
        String observacao,
        String linhaDigitavel) {
}
