package br.com.unifinancas.infrastructure.ocr;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Campos crus que uma etapa da cascata (ADR-016) conseguiu extrair de um
 * documento, antes de virarem o JSON de {@code dados_extraidos}. Todos os
 * campos exceto {@code valor} são "melhor esforço" — a decisão de negócio
 * (T3.1.3) é que uma etapa "tem sucesso" ao achar um {@code valor} confiável;
 * o resto entra em {@code camposFaltando} e a revisão humana completa.
 *
 * @param valor          valor monetário do documento (obrigatório para sucesso)
 * @param vencimento     data de vencimento (boleto/comprovante), ou null
 * @param emissao        data de emissão (nota/cupom), ou null
 * @param descricao      beneficiário / emitente / estabelecimento, ou null
 * @param documentoFiscal CNPJ/CPF ou chave de NF-e, ou null
 * @param linhaDigitavel linha digitável do boleto (47 dígitos), ou null
 * @param textoBruto     texto/OCR completo lido do documento (para a revisão)
 * @param confiancaOcr   confiança média do OCR em [0,1]; null fora da etapa OCR
 */
public record ExtracaoEtapa(
        BigDecimal valor,
        LocalDate vencimento,
        LocalDate emissao,
        String descricao,
        String documentoFiscal,
        String linhaDigitavel,
        String textoBruto,
        Double confiancaOcr) {

    public boolean temValor() {
        return valor != null;
    }
}
