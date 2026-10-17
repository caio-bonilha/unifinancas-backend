package br.com.unifinancas.application.importacao;

/** T4.1.4, RF-21: qual parser (infrastructure/importacao) usar para ler o arquivo enviado - OFX tem endpoint proprio (formato autodescritivo, sem mapeamento de coluna). */
public enum TipoArquivoPlanilha {
    CSV,
    EXCEL
}
