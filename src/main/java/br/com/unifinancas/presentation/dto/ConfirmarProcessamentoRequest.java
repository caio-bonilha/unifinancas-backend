package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * SAD §7.2, Fluxo 6.3 — {@code PATCH /v1/documentos/{id}/confirmar}. Os dados
 * que o usuário revisou; viram um Lançamento de origem DOCUMENTO (RN-08).
 * Mesmo conjunto de campos de {@link CriarLancamentoRequest}.
 */
public record ConfirmarProcessamentoRequest(
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
