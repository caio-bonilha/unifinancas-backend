package br.com.unifinancas.infrastructure.importacao;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * RF-21, T4.1.2: le uma planilha Excel (.xlsx ou .xls legado -
 * {@code WorkbookFactory} decide o formato a partir do proprio arquivo) de
 * extrato importado como {@link TabelaImportada} generica - so a primeira
 * aba (mesmo criterio de simplicidade do {@code OfxParser}, que so le um
 * extrato por arquivo). {@code DataFormatter} converte cada celula para o
 * texto exatamente como o Excel mostraria (respeita o formato de
 * numero/data da propria celula, em vez de expor o valor numerico cru).
 */
public final class ExcelParser {

    private ExcelParser() {
    }

    public static TabelaImportada parse(byte[] conteudo) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(conteudo))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ImportacaoParseException("Planilha nao contem nenhuma aba.");
            }
            Sheet planilha = workbook.getSheetAt(0);
            Iterator<Row> linhas = planilha.iterator();
            if (!linhas.hasNext()) {
                throw new ImportacaoParseException("Planilha vazia.");
            }

            // Locale.US fixo (nao o locale padrao da JVM) - garante separador decimal "." de forma consistente,
            // independente de onde o processo roda, mesmo criterio de ponto decimal do OfxParser/CsvParser.
            DataFormatter formatador = new DataFormatter(Locale.US);
            List<String> cabecalho = extrairLinha(linhas.next(), formatador, -1);
            if (cabecalho.isEmpty()) {
                throw new ImportacaoParseException("Planilha sem colunas no cabecalho.");
            }

            List<List<String>> corpo = new ArrayList<>();
            while (linhas.hasNext()) {
                List<String> valores = extrairLinha(linhas.next(), formatador, cabecalho.size());
                if (valores.stream().anyMatch(valor -> !valor.isBlank())) {
                    corpo.add(valores);
                }
            }
            return new TabelaImportada(cabecalho, corpo);
        } catch (ImportacaoParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportacaoParseException("Nao foi possivel interpretar a planilha: " + e.getMessage(), e);
        }
    }

    /** {@code numeroColunas < 0} usa {@code getLastCellNum()} da propria linha (cabecalho, onde o numero de colunas ainda nao e conhecido). */
    private static List<String> extrairLinha(Row linha, DataFormatter formatador, int numeroColunas) {
        int colunas = numeroColunas >= 0 ? numeroColunas : Math.max(linha.getLastCellNum(), 0);
        List<String> valores = new ArrayList<>();
        for (int i = 0; i < colunas; i++) {
            Cell celula = linha.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            valores.add(celula == null ? "" : formatador.formatCellValue(celula).trim());
        }
        return valores;
    }
}
