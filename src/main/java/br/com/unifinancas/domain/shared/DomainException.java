package br.com.unifinancas.domain.shared;

/**
 * Base para as excecoes de dominio (SAD Sec13.1). Cada subtipo corresponde a
 * uma categoria da taxonomia de erros e e mapeado para um status HTTP fixo
 * pelo handler global de excecoes (Sec13.2); o codigo especifico da regra
 * violada e sempre definido por quem lanca a excecao, nunca pelo handler.
 */
public abstract class DomainException extends RuntimeException {

    private final String codigo;

    protected DomainException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
