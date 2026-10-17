package br.com.unifinancas.infrastructure.ocr;

import br.com.unifinancas.application.documento.OrigemExtracao;
import java.util.List;

/**
 * Formato do JSON gravado em {@code processamento_documento.dados_extraidos}
 * (T3.1.3, ADR-016). Achatado e estável: o {@code ProcessamentoDocumentoOrchestrator}
 * (T3.1.4) grava, e a tela de revisão (T3.1.7) mostra cada campo para o
 * usuário conferir/corrigir antes de gerar Lançamento/Compra (RN-08).
 *
 * <p>Datas em ISO-8601 (string), valor como string decimal com ponto (nunca
 * float — SAD Seção 7.1). {@code camposFaltando} lista os campos esperados
 * para o tipo do documento que a cascata não conseguiu preencher.
 */
public record DadosExtraidos(
        OrigemExtracao origemExtracao,
        Double confiancaOcr,
        String tipoDocumento,
        String valor,
        String vencimento,
        String emissao,
        String descricao,
        String documentoFiscal,
        String linhaDigitavel,
        String textoBruto,
        List<String> camposFaltando) {
}
