package br.com.unifinancas.domain.orcamentometa;

/**
 * T1.5.3, Fluxo 6.4: os dois niveis de alerta de progresso de um Orcamento -
 * AVISO quando atinge o limiar configuravel (Sec4.11, `[Proposto]` 80% por
 * padrao), ESTOURADO quando atinge/ultrapassa 100% do valorLimite (fixo,
 * por definicao - nao configuravel).
 */
public enum SeveridadeAlertaOrcamento {
    AVISO,
    ESTOURADO
}
