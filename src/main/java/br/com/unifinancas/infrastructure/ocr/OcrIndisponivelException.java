package br.com.unifinancas.infrastructure.ocr;

/**
 * A etapa de OCR não pôde rodar (binário do Tesseract ausente, timeout, saída
 * de erro). Não é um erro de negócio — a cascata (ADR-016) apenas trata a
 * etapa como indisponível e segue para a etapa OpenAI.
 */
class OcrIndisponivelException extends RuntimeException {

    OcrIndisponivelException(String mensagem) {
        super(mensagem);
    }

    OcrIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
