package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.movimentacao.OrigemMovimentacao;
import br.com.unifinancas.domain.movimentacao.TipoMovimentacao;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** SAD §7.2, §4.4a. */
public record MovimentacaoResponse(
        UUID id, UUID contaBancariaId, TipoMovimentacao tipo, BigDecimal valor, LocalDate data, String descricao,
        OrigemMovimentacao origem, UUID categoriaId, UUID lancamentoId, UUID grupoTransferenciaId, Instant criadoEm) {
}
