package br.com.unifinancas.infrastructure.importacao;

/** O arquivo (CSV ou Excel) nao pode ser lido como uma tabela. Nao e um erro de negocio - quem chama decide como reportar ao usuario (T4.1.4). */
public class ImportacaoParseException extends RuntimeException {

    public ImportacaoParseException(String mensagem) {
        super(mensagem);
    }

    public ImportacaoParseException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
