package br.com.unifinancas.infrastructure.importacao;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * RF-21, T4.1.2: le um CSV de extrato bancario como {@link TabelaImportada}
 * generica - RFC 4180 (campos entre aspas podem conter o delimitador,
 * quebra de linha e aspas escapadas dobrando-as: {@code ""}). Delimitador
 * (vírgula/ponto-e-virgula - bancos brasileiros costumam usar
 * ponto-e-virgula, ja que virgula e o separador decimal em pt-BR) e charset
 * (BOM UTF-8, senao UTF-8 com fallback para windows-1252 se a decodificacao
 * UTF-8 falhar) sao detectados automaticamente, sem exigir que o usuario
 * informe nada.
 */
public final class CsvParser {

    private CsvParser() {
    }

    public static TabelaImportada parse(byte[] conteudo) {
        return parse(decodificar(conteudo));
    }

    public static TabelaImportada parse(String conteudo) {
        if (conteudo == null || conteudo.isBlank()) {
            throw new ImportacaoParseException("Arquivo CSV vazio.");
        }
        char delimitador = detectarDelimitador(conteudo);
        List<List<String>> registros = tokenizar(conteudo, delimitador);
        if (registros.isEmpty()) {
            throw new ImportacaoParseException("Arquivo CSV vazio.");
        }
        List<String> cabecalho = registros.get(0);
        List<List<String>> linhas = registros.size() > 1 ? registros.subList(1, registros.size()) : List.of();
        return new TabelaImportada(cabecalho, linhas);
    }

    private static String decodificar(byte[] conteudo) {
        if (conteudo.length >= 3 && (conteudo[0] & 0xFF) == 0xEF && (conteudo[1] & 0xFF) == 0xBB && (conteudo[2] & 0xFF) == 0xBF) {
            return new String(conteudo, 3, conteudo.length - 3, StandardCharsets.UTF_8);
        }
        String utf8 = new String(conteudo, StandardCharsets.UTF_8);
        if (utf8.indexOf('�') >= 0) {
            return new String(conteudo, Charset.forName("windows-1252"));
        }
        return utf8;
    }

    private static char detectarDelimitador(String conteudo) {
        int fimPrimeiraLinha = conteudo.indexOf('\n');
        String primeiraLinha = fimPrimeiraLinha >= 0 ? conteudo.substring(0, fimPrimeiraLinha) : conteudo;
        long virgulas = primeiraLinha.chars().filter(c -> c == ',').count();
        long pontoEVirgulas = primeiraLinha.chars().filter(c -> c == ';').count();
        return pontoEVirgulas > virgulas ? ';' : ',';
    }

    /** Tokenizador RFC 4180 - percorre caractere a caractere para respeitar delimitador/quebra de linha dentro de campos entre aspas. */
    private static List<List<String>> tokenizar(String conteudo, char delimitador) {
        List<List<String>> registros = new ArrayList<>();
        List<String> registroAtual = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean dentroDeAspas = false;
        boolean linhaTemConteudo = false;

        int tamanho = conteudo.length();
        int i = 0;
        while (i < tamanho) {
            char c = conteudo.charAt(i);
            if (dentroDeAspas) {
                if (c == '"') {
                    if (i + 1 < tamanho && conteudo.charAt(i + 1) == '"') {
                        campo.append('"');
                        i += 2;
                        continue;
                    }
                    dentroDeAspas = false;
                    i++;
                    continue;
                }
                campo.append(c);
                i++;
                continue;
            }
            if (c == '"' && campo.isEmpty()) {
                dentroDeAspas = true;
                linhaTemConteudo = true;
                i++;
                continue;
            }
            if (c == delimitador) {
                registroAtual.add(campo.toString());
                campo.setLength(0);
                linhaTemConteudo = true;
                i++;
                continue;
            }
            if (c == '\r' || c == '\n') {
                if (linhaTemConteudo || !campo.isEmpty() || !registroAtual.isEmpty()) {
                    registroAtual.add(campo.toString());
                    registros.add(registroAtual);
                }
                registroAtual = new ArrayList<>();
                campo.setLength(0);
                linhaTemConteudo = false;
                i += (c == '\r' && i + 1 < tamanho && conteudo.charAt(i + 1) == '\n') ? 2 : 1;
                continue;
            }
            campo.append(c);
            linhaTemConteudo = true;
            i++;
        }
        if (linhaTemConteudo || !campo.isEmpty() || !registroAtual.isEmpty()) {
            registroAtual.add(campo.toString());
            registros.add(registroAtual);
        }
        registros.removeIf(registro -> registro.size() == 1 && registro.get(0).isEmpty());
        return registros;
    }
}
