package br.com.unifinancas.domain.shared;

/**
 * Recurso inexistente ou pertencente a outro Ambiente Financeiro (SAD Sec13.1,
 * Sec8.7) - sempre mapeada para HTTP 404, nunca 403, para nao revelar a um
 * tenant que um recurso de outro tenant existe.
 */
public class RecursoNaoEncontradoException extends DomainException {

    public RecursoNaoEncontradoException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
