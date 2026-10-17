package br.com.unifinancas.application.documento;

/**
 * Qual etapa da cascata de extração (ADR-016) produziu os {@code
 * dadosExtraidos} de um {@link br.com.unifinancas.domain.documento.ProcessamentoDocumento}.
 * Persistido como string dentro do JSON de {@code dados_extraidos} (campo
 * {@code origemExtracao}) para observabilidade da taxa de acerto por etapa
 * (SAD Seção 14.5) e para decidir no futuro se vale contratar um provedor pago.
 */
public enum OrigemExtracao {
    /** XML de NF-e/NFC-e enviado direto — parsing estruturado, sem OCR. */
    XML,
    /** Camada de texto de um PDF nativo, lida pelo PDFBox. */
    PDFBOX,
    /** OCR próprio (Tesseract como subprocesso) sobre imagem/página rasterizada. */
    OCR,
    /** Último recurso: OpenAI API (visão), opt-in por Ambiente Financeiro. */
    IA
}
