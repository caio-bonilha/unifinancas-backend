package br.com.unifinancas.domain.shared;

/**
 * Violacao de uma regra de negocio (SAD Sec13.1/13.2) - ex.: transferencia
 * para a mesma conta, limite de orcamento invalido. Mapeada para HTTP 422,
 * com o codigo especifico da regra (ex.: TRANSFERENCIA_MESMA_CONTA).
 */
public class RegraDeNegocioException extends DomainException {

    public RegraDeNegocioException(String codigo, String mensagem) {
        super(codigo, mensagem);
    }
}
