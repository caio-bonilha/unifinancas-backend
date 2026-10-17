package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * SAD §7.2 - PATCH /v1/lancamentos/{id}. Campos usados dependem de
 * {@code acao} (validado no controller, nao com Bean Validation simples):
 * EDITAR usa os campos descritivos; CONFIRMAR_PAGAMENTO/CONFIRMAR_RECEBIMENTO
 * usam {@code dataPagamento}; CANCELAR nao usa nenhum campo adicional.
 */
public record AtualizarLancamentoRequest(
        @NotNull(message = "acao e obrigatoria") AcaoLancamento acao,
        BigDecimal valor,
        LocalDate dataEmissao,
        LocalDate dataVencimento,
        UUID categoriaId,
        UUID contatoId,
        UUID contaBancariaId,
        FormaPagamento formaPagamento,
        String descricao,
        String observacao,
        String linhaDigitavel,
        LocalDate dataPagamento) {
}
