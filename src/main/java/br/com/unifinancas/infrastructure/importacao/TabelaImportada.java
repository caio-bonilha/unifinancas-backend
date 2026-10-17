package br.com.unifinancas.infrastructure.importacao;

import java.util.List;

/**
 * RF-21, T4.1.2: leitura generica de um CSV/Excel de extrato importado - so
 * a primeira linha nao vazia vira {@code cabecalho}, as demais viram
 * {@code linhas} (cada uma com o mesmo numero de colunas do cabecalho).
 * Diferente do OFX (formato padronizado, {@link ExtratoOfx} ja sai com
 * campos de transacao reconhecidos), CSV/Excel de banco nao tem layout
 * fixo - decisao consultada com o usuario: {@code CsvParser}/
 * {@code ExcelParser} nao tentam adivinhar "qual coluna e data/valor/
 * descricao"; isso fica para a tela de revisao (T4.1.4), que mapeia as
 * colunas desta tabela para os campos de um Lancamento.
 */
public record TabelaImportada(List<String> cabecalho, List<List<String>> linhas) {
}
