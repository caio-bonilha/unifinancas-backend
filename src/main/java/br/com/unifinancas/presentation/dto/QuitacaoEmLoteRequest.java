package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * SAD §7.2 - PATCH /v1/lancamentos/quitacao-em-lote (ADR-015): confirma
 * pagamento/recebimento de varios Lancamentos, todos do mesmo tipo.
 * contaBancariaId/formaPagamento valem so como fallback dos Lancamentos que
 * ainda nao os tem (mesma regra de resolucao por campo da confirmacao
 * individual) - por isso continuam opcionais aqui.
 */
public record QuitacaoEmLoteRequest(
        @NotEmpty(message = "ids e obrigatorio") List<UUID> ids,
        @NotNull(message = "dataPagamento e obrigatoria") LocalDate dataPagamento,
        UUID contaBancariaId,
        FormaPagamento formaPagamento) {
}
