package br.com.unifinancas.presentation.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** SAD §7.2, §4.5. */
public record CartaoResponse(
        UUID id, UUID contaBancariaId, String nome, BigDecimal limite, int diaFechamento, int diaVencimento,
        boolean ativo, BigDecimal taxaJurosRotativoMensal) {
}
