package br.com.unifinancas.application.lancamentorecorrente;

import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.lancamentorecorrente.FrequenciaRecorrencia;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LancamentoRecorrenteResumo(UUID id, String descricao, TipoLancamento tipo, BigDecimal valor,
        UUID categoriaId, UUID contaBancariaId, FrequenciaRecorrencia frequencia,
        Integer diaReferencia, LocalDate dataInicio, LocalDate dataFim, boolean ativo) {
}
