package br.com.unifinancas.application.lancamento;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import br.com.unifinancas.domain.lancamento.OrigemLancamento;
import br.com.unifinancas.domain.lancamento.StatusLancamento;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LancamentoResumo(
        UUID id, TipoLancamento tipo, StatusLancamento status, BigDecimal valor, LocalDate dataEmissao,
        LocalDate dataVencimento, LocalDate dataPagamento, UUID categoriaId, UUID contatoId, UUID contaBancariaId,
        FormaPagamento formaPagamento, OrigemLancamento origem, String descricao, String observacao,
        String linhaDigitavel) {
}
