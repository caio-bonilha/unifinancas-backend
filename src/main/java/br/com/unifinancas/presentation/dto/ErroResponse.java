package br.com.unifinancas.presentation.dto;

import java.time.Instant;
import java.util.List;

/** Payload de erro padrao de toda a API (SAD Sec7.5). */
public record ErroResponse(
        String codigo,
        String mensagem,
        List<ErroDetalhe> detalhes,
        Instant timestamp,
        String traceId) {
}
