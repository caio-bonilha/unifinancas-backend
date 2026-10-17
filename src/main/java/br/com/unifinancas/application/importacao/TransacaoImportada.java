package br.com.unifinancas.application.importacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * T4.1.3, RF-21: uma transacao normalizada, de qualquer origem (OFX ja
 * extrai isso pronto - {@code TransacaoOfx}; CSV/Excel so chegam aqui
 * depois que a tela de revisao, T4.1.4, mapear as colunas da
 * {@code TabelaImportada} genérica para estes campos).
 * {@code identificadorExterno}: o FITID do OFX quando existir; nulo para
 * CSV/Excel (sem identificador proprio) - {@link ImportacaoService} resolve
 * um identificador sintetico nesse caso.
 */
public record TransacaoImportada(LocalDate data, BigDecimal valor, String descricao, String identificadorExterno) {
}
