package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.lancamentorecorrente.FrequenciaRecorrencia;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * SAD §7.2 - PATCH /v1/lancamentos-recorrentes/{id}. Campos usados dependem
 * de {@code acao} (validado no controller, nao com Bean Validation simples):
 * EDITAR usa os campos descritivos; DESATIVAR/REATIVAR nao usam nenhum
 * campo adicional (mesmo padrao de AtualizarLancamentoRequest, T1.3.3).
 */
public record AtualizarLancamentoRecorrenteRequest(
        @NotNull(message = "acao e obrigatoria") AcaoLancamentoRecorrente acao,
        String descricao,
        BigDecimal valor,
        UUID categoriaId,
        UUID contaBancariaId,
        FrequenciaRecorrencia frequencia,
        Integer diaReferencia,
        LocalDate dataInicio,
        LocalDate dataFim) {
}
