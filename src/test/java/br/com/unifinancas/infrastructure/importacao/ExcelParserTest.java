package br.com.unifinancas.infrastructure.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelParserTest {

    private byte[] paraBytes(Workbook workbook) {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            workbook.write(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void parseiaXlsxComCabecalhoEDuasLinhas() {
        Workbook workbook = new XSSFWorkbook();
        Sheet planilha = workbook.createSheet("Extrato");
        Row cabecalho = planilha.createRow(0);
        cabecalho.createCell(0).setCellValue("Data");
        cabecalho.createCell(1).setCellValue("Descricao");
        cabecalho.createCell(2).setCellValue("Valor");

        Row linha1 = planilha.createRow(1);
        linha1.createCell(0).setCellValue("05/08/2026");
        linha1.createCell(1).setCellValue("Padaria");
        linha1.createCell(2).setCellValue(-45.90);

        Row linha2 = planilha.createRow(2);
        linha2.createCell(0).setCellValue("10/08/2026");
        linha2.createCell(1).setCellValue("Salario");
        linha2.createCell(2).setCellValue(1500.00);

        byte[] conteudo = paraBytes(workbook);

        TabelaImportada tabela = ExcelParser.parse(conteudo);

        assertThat(tabela.cabecalho()).containsExactly("Data", "Descricao", "Valor");
        assertThat(tabela.linhas()).hasSize(2);
        assertThat(tabela.linhas().get(0)).containsExactly("05/08/2026", "Padaria", "-45.9");
        assertThat(tabela.linhas().get(1)).containsExactly("10/08/2026", "Salario", "1500");
    }

    @Test
    void lePrimeiraAbaQuandoHaMaisDeUma() {
        Workbook workbook = new XSSFWorkbook();
        workbook.createSheet("Resumo").createRow(0).createCell(0).setCellValue("Nao deve ser lida");
        Sheet primeiraAba = workbook.createSheet("0-Extrato");
        // Garante que "0-Extrato" (nome que ordena antes de "Resumo") seja a PRIMEIRA aba criada, nao por nome.
        workbook.setSheetOrder("0-Extrato", 0);
        primeiraAba.createRow(0).createCell(0).setCellValue("Data");
        primeiraAba.createRow(1).createCell(0).setCellValue("05/08/2026");

        byte[] conteudo = paraBytes(workbook);

        TabelaImportada tabela = ExcelParser.parse(conteudo);

        assertThat(tabela.cabecalho()).containsExactly("Data");
        assertThat(tabela.linhas().get(0)).containsExactly("05/08/2026");
    }

    @Test
    void linhasTotalmenteEmBrancoSaoIgnoradas() {
        Workbook workbook = new XSSFWorkbook();
        Sheet planilha = workbook.createSheet("Extrato");
        planilha.createRow(0).createCell(0).setCellValue("Data");
        planilha.createRow(1).createCell(0).setCellValue("05/08/2026");
        planilha.createRow(2); // linha em branco, sem nenhuma celula
        planilha.createRow(3).createCell(0).setCellValue("10/08/2026");

        byte[] conteudo = paraBytes(workbook);

        TabelaImportada tabela = ExcelParser.parse(conteudo);

        assertThat(tabela.linhas()).hasSize(2);
    }

    @Test
    void planilhaSemLinhaAlgumaLancaImportacaoParseException() {
        Workbook workbook = new XSSFWorkbook();
        workbook.createSheet("Vazia");
        byte[] conteudo = paraBytes(workbook);

        assertThatThrownBy(() -> ExcelParser.parse(conteudo)).isInstanceOf(ImportacaoParseException.class);
    }

    @Test
    void arquivoQueNaoEUmaPlanilhaLancaImportacaoParseException() {
        byte[] conteudo = "isto nao e um xlsx".getBytes();

        assertThatThrownBy(() -> ExcelParser.parse(conteudo)).isInstanceOf(ImportacaoParseException.class);
    }
}
