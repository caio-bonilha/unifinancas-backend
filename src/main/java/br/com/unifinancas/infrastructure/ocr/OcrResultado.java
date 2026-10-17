package br.com.unifinancas.infrastructure.ocr;

/**
 * Saída do Tesseract: o texto reconhecido e a confiança média das palavras
 * (0..1). A cascata (ADR-016, etapa 3) só aceita o resultado quando
 * {@code confiancaMedia >= unifinancas.ocr.confianca-minima}.
 */
record OcrResultado(String texto, double confiancaMedia) {
}
