package br.com.unifinancas.infrastructure.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CsvParserTest {

    @Test
    void parseiaCsvComVirgulaComoDelimitador() {
        String csv = """
                Data,Descricao,Valor
                05/08/2026,Padaria,-45.90
                10/08/2026,Salario,1500.00
                """;

        TabelaImportada tabela = CsvParser.parse(csv);

        assertThat(tabela.cabecalho()).containsExactly("Data", "Descricao", "Valor");
        assertThat(tabela.linhas()).hasSize(2);
        assertThat(tabela.linhas().get(0)).containsExactly("05/08/2026", "Padaria", "-45.90");
        assertThat(tabela.linhas().get(1)).containsExactly("10/08/2026", "Salario", "1500.00");
    }

    @Test
    void detectaPontoEVirgulaComoDelimitadorQuandoPredominaNaPrimeiraLinha() {
        String csv = "Data;Descricao;Valor\n05/08/2026;Padaria;-45,90\n";

        TabelaImportada tabela = CsvParser.parse(csv);

        assertThat(tabela.cabecalho()).containsExactly("Data", "Descricao", "Valor");
        assertThat(tabela.linhas().get(0)).containsExactly("05/08/2026", "Padaria", "-45,90");
    }

    @Test
    void camposEntreAspasPodemConterODelimitadorEQuebraDeLinha() {
        String csv = "Data,Descricao,Valor\n"
                + "05/08/2026,\"Padaria, pao e leite\",-45.90\n"
                + "06/08/2026,\"Linha 1\nLinha 2\",-10.00\n";

        TabelaImportada tabela = CsvParser.parse(csv);

        assertThat(tabela.linhas().get(0)).containsExactly("05/08/2026", "Padaria, pao e leite", "-45.90");
        assertThat(tabela.linhas().get(1)).containsExactly("06/08/2026", "Linha 1\nLinha 2", "-10.00");
    }

    @Test
    void aspasDuplicadasDentroDeCampoEntreAspasViramUmaAspaLiteral() {
        String csv = "Descricao\n\"Loja \"\"Bom Preco\"\"\"\n";

        TabelaImportada tabela = CsvParser.parse(csv);

        assertThat(tabela.linhas().get(0)).containsExactly("Loja \"Bom Preco\"");
    }

    @Test
    void linhasEmBrancoSaoIgnoradas() {
        String csv = "Data,Valor\n05/08/2026,-45.90\n\n10/08/2026,1500.00\n";

        TabelaImportada tabela = CsvParser.parse(csv);

        assertThat(tabela.linhas()).hasSize(2);
    }

    @Test
    void arquivoVazioLancaImportacaoParseException() {
        assertThatThrownBy(() -> CsvParser.parse("")).isInstanceOf(ImportacaoParseException.class);
        assertThatThrownBy(() -> CsvParser.parse((String) null)).isInstanceOf(ImportacaoParseException.class);
    }

    @Test
    void removeBomUtf8AoDecodificarBytes() {
        byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] corpo = "Data,Valor\n05/08/2026,-45.90\n".getBytes(StandardCharsets.UTF_8);
        byte[] conteudo = new byte[bom.length + corpo.length];
        System.arraycopy(bom, 0, conteudo, 0, bom.length);
        System.arraycopy(corpo, 0, conteudo, bom.length, corpo.length);

        TabelaImportada tabela = CsvParser.parse(conteudo);

        assertThat(tabela.cabecalho().get(0)).isEqualTo("Data");
    }

    @Test
    void decodificaWindows1252QuandoUtf8FalhaComAcentos() {
        String csv = "Descricao\nPadaria São José\n";
        byte[] conteudo = csv.getBytes(Charset.forName("windows-1252"));

        TabelaImportada tabela = CsvParser.parse(conteudo);

        assertThat(tabela.linhas().get(0).get(0)).isEqualTo("Padaria São José");
    }
}
