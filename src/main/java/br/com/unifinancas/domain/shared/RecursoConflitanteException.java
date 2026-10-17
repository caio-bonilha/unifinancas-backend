package br.com.unifinancas.domain.shared;

/**
 * Conflito com o estado atual dos dados (SAD Sec13.1/13.2) - ex.: e-mail ja
 * cadastrado. Mapeada para HTTP 409, com o codigo especifico do conflito
 * (ex.: EMAIL_JA_CADASTRADO).
 */
public class RecursoConflitanteException extends DomainException {

    public RecursoConflitanteException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
