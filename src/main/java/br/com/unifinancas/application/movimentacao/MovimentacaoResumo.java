package br.com.unifinancas.application.movimentacao;

import br.com.unifinancas.domain.movimentacao.OrigemMovimentacao;
import br.com.unifinancas.domain.movimentacao.TipoMovimentacao;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MovimentacaoResumo(
        UUID id, UUID contaBancariaId, TipoMovimentacao tipo, BigDecimal valor, LocalDate data, String descricao,
        OrigemMovimentacao origem, UUID categoriaId, UUID lancamentoId, UUID grupoTransferenciaId, Instant criadoEm) {
}
