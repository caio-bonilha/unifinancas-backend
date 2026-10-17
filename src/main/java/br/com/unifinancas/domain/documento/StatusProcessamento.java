package br.com.unifinancas.domain.documento;

/**
 * Ciclo de vida do pipeline de OCR/IA sobre um Documento - SAD Sec4.15,
 * diagrama de estados Sec6.5.
 *
 * <pre>
 * [*] -> PENDENTE
 * PENDENTE -> PROCESSANDO
 * PROCESSANDO -> AGUARDANDO_REVISAO : extracao concluida
 * PROCESSANDO -> ERRO : falha no provedor
 * AGUARDANDO_REVISAO -> CONFIRMADO : usuario confirma
 * ERRO -> PROCESSANDO : novo processamento (retry)
 * CONFIRMADO -> [*]
 * </pre>
 */
public enum StatusProcessamento {
    PENDENTE,
    PROCESSANDO,
    AGUARDANDO_REVISAO,
    CONFIRMADO,
    ERRO
}
