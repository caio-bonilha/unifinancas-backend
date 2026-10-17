package br.com.unifinancas.application.cartao;

import java.math.BigDecimal;
import java.util.UUID;

public record CartaoResumo(
        UUID id, UUID contaBancariaId, String nome, BigDecimal limite, int diaFechamento, int diaVencimento,
        boolean ativo, BigDecimal taxaJurosRotativoMensal) {
}
