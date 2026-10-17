package br.com.unifinancas.application.documento;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Os campos que o usuário revisou e confirmou sobre um documento processado
 * (T3.1.5, Fluxo 6.3). Viram um Lançamento de origem DOCUMENTO — nunca
 * automaticamente (RN-08). Mesmo conjunto de campos de um Lançamento manual.
 */
public record DadosRevisadosLancamento(
        TipoLancamento tipo,
        BigDecimal valor,
        LocalDate dataEmissao,
        LocalDate dataVencimento,
        UUID categoriaId,
        UUID contatoId,
        UUID contaBancariaId,
        FormaPagamento formaPagamento,
        String descricao,
        String observacao,
        String linhaDigitavel) {
}
