package br.com.unifinancas.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** SAD §7.2, RF-21, T4.1.4 - POST /v1/importacoes: confirma a importacao (cria os Lancamentos das transacoes revisadas). */
public record ConfirmarImportacaoRequest(
        @NotNull(message = "contaBancariaId e obrigatorio") UUID contaBancariaId,
        @NotEmpty(message = "transacoes e obrigatorio") List<@Valid TransacaoConfirmadaRequest> transacoes) {
}
