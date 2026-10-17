package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.lancamentorecorrente.FrequenciaRecorrencia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** SAD §7.2 - POST /v1/lancamentos-recorrentes (RF-11). */
public record CriarLancamentoRecorrenteRequest(
        @NotBlank(message = "descricao e obrigatoria") String descricao,
        @NotNull(message = "tipo e obrigatorio") TipoLancamento tipo,
        @NotNull(message = "valor e obrigatorio") BigDecimal valor,
        UUID categoriaId,
        UUID contaBancariaId,
        @NotNull(message = "frequencia e obrigatoria") FrequenciaRecorrencia frequencia,
        Integer diaReferencia,
        @NotNull(message = "dataInicio e obrigatoria") LocalDate dataInicio,
        LocalDate dataFim) {
}
