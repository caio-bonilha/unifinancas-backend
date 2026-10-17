package br.com.unifinancas.application.documento;

/**
 * Porta de saída para a extração de dados de documentos (boleto, cupom fiscal,
 * nota fiscal/XML, comprovante) — RF-20, ADR-006. A implementação é o adapter
 * de cascata em {@code infrastructure/ocr} (ADR-016): XML NF-e → PDFBox →
 * Tesseract próprio → OpenAI API (último recurso, opt-in por Ambiente
 * Financeiro). O domínio e a aplicação não conhecem nenhuma dessas
 * tecnologias — só esta interface de um método.
 *
 * <p>Chamada exclusivamente pelo {@code ProcessamentoDocumentoOrchestrator}
 * (worker assíncrono, T3.1.4, Fluxo 6.3) — nunca no caminho síncrono de uma
 * requisição HTTP (SAD Seção 3.4/16.6).
 */
public interface ProcessamentoDocumentoPort {

    ResultadoExtracao extrair(EntradaExtracao entrada);
}
