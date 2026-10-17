package br.com.unifinancas.infrastructure.importacao;

/** O arquivo nao e um OFX valido (estrutura, data ou valor ilegivel). Nao e um erro de negocio - quem chama decide como reportar ao usuario (T4.1.4). */
public class OfxParseException extends RuntimeException {

    public OfxParseException(String mensagem) {
        super(mensagem);
    }

    public OfxParseException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
