package br.com.unifinancas.infrastructure.ocr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parsers "próprios" da cascata (ADR-016, etapa 2/3): dado um texto plano
 * (camada de texto do PDF via PDFBox, ou saída do Tesseract), tenta achar os
 * campos de um documento financeiro brasileiro. Sem estado — usado pelas
 * etapas {@link EtapaPdfBox} e {@link EtapaOcrTesseract}.
 *
 * <p>Caminhos determinísticos (mais confiáveis) para o valor:
 * <ul>
 *   <li><b>linha digitável de cobrança bancária</b> (47 dígitos, FEBRABAN) —
 *       codifica valor e fator de vencimento;</li>
 *   <li><b>linha digitável de arrecadação / concessionária</b> (48 dígitos,
 *       começa com 8) — codifica o valor nas posições 5-15 do código de
 *       barras. É o caso de conta de luz/água/gás.</li>
 * </ul>
 * Sem nenhuma das duas, cai em heurísticas de regex, nesta ordem: valor
 * mascarado ({@code *******253,26}), valor prefixado por {@code R$}, rótulo
 * forte ("total a pagar"/"valor a pagar"), e por fim o maior valor do texto
 * (ignorando linhas de tributo/tarifa/consumo).
 */
final class ExtratorDeCampos {

    private static final Logger log = LoggerFactory.getLogger(ExtratorDeCampos.class);

    /** Base do fator de vencimento FEBRABAN (07/10/1997). */
    private static final LocalDate BASE_FATOR_VENCIMENTO = LocalDate.of(1997, 10, 7);
    /** Reinício do ciclo do fator em 22/02/2025 (Comunicado FEBRABAN 082098/2018): fator volta a 1000. */
    private static final LocalDate BASE_FATOR_VENCIMENTO_NOVO_CICLO = LocalDate.of(2025, 2, 22);

    // Só dígitos, ponto, espaço/tab e traço — NUNCA quebra de linha (senão o
    // match greedy engole dígitos da linha seguinte e o tamanho não bate).
    private static final Pattern SEQUENCIA_DIGITAVEL = Pattern.compile("(\\d[\\d.\\- \\t]{40,90}\\d)");
    private static final Pattern VALOR_BR = Pattern.compile("(\\d{1,3}(?:\\.\\d{3})*,\\d{2})");
    private static final Pattern VALOR_MASCARADO = Pattern.compile("\\*{3,}\\s*R?\\$?\\s*(\\d[\\d.]*,\\d{2})");
    private static final Pattern VALOR_COM_REAL = Pattern.compile("R\\$\\s*(\\d{1,3}(?:\\.\\d{3})*,\\d{2})");
    private static final Pattern DATA_BR = Pattern.compile("(\\d{2})[-/.](\\d{2})[-/.](\\d{4})");
    private static final Pattern DATA_ISO = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern CNPJ = Pattern.compile("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}");
    private static final Pattern CPF = Pattern.compile("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
    private static final Pattern CHAVE_NFE = Pattern.compile("(?<!\\d)(\\d{44})(?!\\d)");
    private static final Pattern ROTULO_BENEFICIARIO = Pattern.compile(
            "(?i)(?:benefici[aá]rio|cedente|raz[aã]o social|emitente|estabelecimento|titular)\\s*[:\\-]?\\s*(.+)");

    /** Linhas de composição de tributo/tarifa/consumo — nunca são o valor a pagar. */
    private static final Pattern LINHA_NAO_VALOR = Pattern.compile(
            "(?i)(pis|pasep|cofins|icms|\\biss\\b|tributos?|al[ií]quota|base de c[aá]lculo|base calc"
                    + "|tarifa|pre[çc]o unit|consumo|leitura|kwh|m3|m³|bandeira|\\bcip\\b|contrib|ilumina)");

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private ExtratorDeCampos() {
    }

    static ExtracaoEtapa extrair(String texto, String textoBruto, Double confiancaOcr) {
        if (texto == null || texto.isBlank()) {
            return new ExtracaoEtapa(null, null, null, null, null, null, textoBruto, confiancaOcr);
        }
        List<String> linhas = texto.lines().map(String::strip).filter(l -> !l.isBlank()).toList();

        String linha47 = acharLinhaDigitavelCobranca(texto).orElse(null);
        String codigoArrecadacao = acharCodigoArrecadacao(texto).orElse(null);
        String linhaDigitavel = linha47 != null ? linha47 : codigoArrecadacao;

        BigDecimal valor = null;
        LocalDate vencimento = null;
        if (linha47 != null) {
            valor = valorDaLinhaDigitavel(linha47).orElse(null);
            vencimento = vencimentoDaLinhaDigitavel(linha47).orElse(null);
        }
        if (valor == null && codigoArrecadacao != null) {
            valor = valorDoCodigoArrecadacao(codigoArrecadacao).orElse(null);
        }
        if (valor == null) {
            valor = acharValor(linhas, texto).orElse(null);
        }
        if (vencimento == null) {
            // O fallback "data mais no futuro" só faz sentido quando o documento
            // é claramente uma conta a pagar (tem linha digitável / código de
            // arrecadação) - num cupom fiscal, por exemplo, não há vencimento.
            boolean contaAPagar = linhaDigitavel != null;
            vencimento = acharDataComRotulo(linhas, "vencimento", "vencto", "venc.")
                    .or(() -> contaAPagar ? dataMaisRecentePlausivel(linhas) : Optional.empty())
                    .orElse(null);
        }
        LocalDate emissao = acharDataComRotulo(linhas, "emiss", "data de emiss", "emitido", "apresenta").orElse(null);
        String descricao = acharDescricao(linhas).orElse(null);
        String documentoFiscal = acharDocumentoFiscal(texto).orElse(null);

        return new ExtracaoEtapa(valor, vencimento, emissao, descricao, documentoFiscal, linhaDigitavel,
                textoBruto, confiancaOcr);
    }

    // --- linha digitável -------------------------------------------------------

    static Optional<String> acharLinhaDigitavelCobranca(String texto) {
        return primeiraSequenciaComTamanho(texto, 47);
    }

    /** Código de arrecadação/concessionária: 48 dígitos, começa com 8. */
    static Optional<String> acharCodigoArrecadacao(String texto) {
        return primeiraSequenciaComTamanho(texto, 48).filter(s -> s.startsWith("8"));
    }

    private static Optional<String> primeiraSequenciaComTamanho(String texto, int tamanho) {
        Matcher m = SEQUENCIA_DIGITAVEL.matcher(texto);
        while (m.find()) {
            String somenteDigitos = m.group(1).replaceAll("\\D", "");
            if (somenteDigitos.length() == tamanho) {
                return Optional.of(somenteDigitos);
            }
        }
        return Optional.empty();
    }

    /** Valor (posições 38-47 da linha digitável de cobrança bancária, em centavos). Vazio quando zerado. */
    static Optional<BigDecimal> valorDaLinhaDigitavel(String linha47) {
        if (linha47 == null || linha47.length() != 47) {
            return Optional.empty();
        }
        return centavosParaValor(linha47.substring(37, 47));
    }

    /**
     * Valor do código de arrecadação (48 dígitos): reconstrói o código de
     * barras de 44 (11 significativos de cada bloco de 12) e lê as posições
     * 5-15 (11 dígitos = centavos). Só quando o dígito 3 é 6/7/8/9 (indica
     * "valor efetivo/referência com 2 casas").
     */
    static Optional<BigDecimal> valorDoCodigoArrecadacao(String linha48) {
        if (linha48 == null || linha48.length() != 48) {
            return Optional.empty();
        }
        String barra = linha48.substring(0, 11) + linha48.substring(12, 23)
                + linha48.substring(24, 35) + linha48.substring(36, 47);
        char idValor = barra.charAt(2);
        if ("6789".indexOf(idValor) < 0) {
            return Optional.empty();
        }
        return centavosParaValor(barra.substring(4, 15));
    }

    private static Optional<BigDecimal> centavosParaValor(String centavosStr) {
        try {
            long centavos = Long.parseLong(centavosStr);
            return centavos == 0 ? Optional.empty() : Optional.of(BigDecimal.valueOf(centavos, 2));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Fator de vencimento (posições 34-37 da linha digitável) → data. Vazio
     * quando fator 0. O fator tem 4 dígitos e cicla (reinício em 22/02/2025);
     * desempata escolhendo o candidato numa janela plausível em torno de hoje.
     */
    static Optional<LocalDate> vencimentoDaLinhaDigitavel(String linha47) {
        if (linha47 == null || linha47.length() != 47) {
            return Optional.empty();
        }
        int fator;
        try {
            fator = Integer.parseInt(linha47.substring(33, 37));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (fator == 0) {
            return Optional.empty();
        }
        LocalDate hoje = LocalDate.now();
        LocalDate cicloAntigo = BASE_FATOR_VENCIMENTO.plusDays(fator);
        LocalDate cicloNovo = fator >= 1000 ? BASE_FATOR_VENCIMENTO_NOVO_CICLO.plusDays(fator - 1000L) : null;

        if (plausivel(cicloAntigo, hoje)) {
            return Optional.of(cicloAntigo);
        }
        if (cicloNovo != null && plausivel(cicloNovo, hoje)) {
            return Optional.of(cicloNovo);
        }
        return Optional.of(cicloAntigo);
    }

    private static boolean plausivel(LocalDate data, LocalDate hoje) {
        return !data.isBefore(hoje.minusYears(5)) && !data.isAfter(hoje.plusYears(10));
    }

    // --- valor ---------------------------------------------------------------

    private static Optional<BigDecimal> acharValor(List<String> linhas, String texto) {
        // 1. valor mascarado (ex.: "*******253,26") - indicador forte de "valor a pagar".
        Matcher mascarado = VALOR_MASCARADO.matcher(texto);
        if (mascarado.find()) {
            BigDecimal v = brParaDecimal(mascarado.group(1));
            if (v != null && v.signum() > 0) {
                return Optional.of(v);
            }
        }

        // 2. maior valor prefixado por "R$" que nao esteja numa linha de tributo/tarifa.
        BigDecimal maiorComReal = null;
        BigDecimal rotuloForte = null;
        BigDecimal maiorGeral = null;
        for (String linha : linhas) {
            boolean linhaDeComposicao = LINHA_NAO_VALOR.matcher(linha).find();
            String lower = linha.toLowerCase();
            boolean rotulo = lower.contains("total a pagar") || lower.contains("valor a pagar")
                    || lower.contains("valor cobrado") || lower.contains("total da fatura")
                    || lower.contains("valor do documento");

            Matcher comReal = VALOR_COM_REAL.matcher(linha);
            while (comReal.find()) {
                BigDecimal v = brParaDecimal(comReal.group(1));
                if (v == null || linhaDeComposicao) {
                    continue;
                }
                maiorComReal = maior(maiorComReal, v);
            }

            Matcher qualquer = VALOR_BR.matcher(linha);
            while (qualquer.find()) {
                BigDecimal v = brParaDecimal(qualquer.group(1));
                if (v == null) {
                    continue;
                }
                if (rotulo && !linhaDeComposicao) {
                    rotuloForte = maior(rotuloForte, v);
                }
                if (!linhaDeComposicao) {
                    maiorGeral = maior(maiorGeral, v);
                }
            }
        }
        if (maiorComReal != null) {
            return Optional.of(maiorComReal);
        }
        if (rotuloForte != null) {
            return Optional.of(rotuloForte);
        }
        return Optional.ofNullable(maiorGeral);
    }

    private static BigDecimal maior(BigDecimal atual, BigDecimal candidato) {
        return atual == null || candidato.compareTo(atual) > 0 ? candidato : atual;
    }

    // --- datas -------------------------------------------------------------

    /**
     * Data associada a um rótulo. Aceita o rótulo e a data na mesma linha ou
     * numa das 3 linhas seguintes (layout de formulário separa rótulo e valor).
     */
    private static Optional<LocalDate> acharDataComRotulo(List<String> linhas, String... rotulos) {
        for (int i = 0; i < linhas.size(); i++) {
            String lower = linhas.get(i).toLowerCase();
            boolean temRotulo = false;
            for (String r : rotulos) {
                if (lower.contains(r)) {
                    temRotulo = true;
                    break;
                }
            }
            if (!temRotulo) {
                continue;
            }
            Optional<LocalDate> naMesmaLinha = primeiraData(linhas.get(i));
            if (naMesmaLinha.isPresent()) {
                return naMesmaLinha;
            }
            for (int j = i + 1; j < Math.min(linhas.size(), i + 4); j++) {
                Optional<LocalDate> proxima = primeiraData(linhas.get(j));
                if (proxima.isPresent()) {
                    return proxima;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Última data (mais no futuro) que caia numa janela plausível — em conta
     * de concessionária, o vencimento costuma ser a data mais adiante do
     * documento. Ignora linhas de leitura/consumo.
     */
    private static Optional<LocalDate> dataMaisRecentePlausivel(List<String> linhas) {
        LocalDate hoje = LocalDate.now();
        LocalDate melhor = null;
        for (String linha : linhas) {
            // "leitura" (do LINHA_NAO_VALOR) ja descarta "DATA DA PROXIMA LEITURA ...".
            if (LINHA_NAO_VALOR.matcher(linha).find()) {
                continue;
            }
            Matcher m = DATA_BR.matcher(linha);
            while (m.find()) {
                LocalDate d = parseDataBr(m.group(1), m.group(2), m.group(3));
                if (d != null && plausivel(d, hoje) && (melhor == null || d.isAfter(melhor))) {
                    melhor = d;
                }
            }
        }
        return Optional.ofNullable(melhor);
    }

    static Optional<LocalDate> primeiraData(String texto) {
        Matcher br = DATA_BR.matcher(texto);
        if (br.find()) {
            LocalDate d = parseDataBr(br.group(1), br.group(2), br.group(3));
            if (d != null) {
                return Optional.of(d);
            }
        }
        Matcher iso = DATA_ISO.matcher(texto);
        if (iso.find()) {
            try {
                return Optional.of(LocalDate.parse(iso.group()));
            } catch (RuntimeException e) {
                log.debug("Data ISO invalida: {}", iso.group());
            }
        }
        return Optional.empty();
    }

    private static LocalDate parseDataBr(String dia, String mes, String ano) {
        try {
            return LocalDate.parse(dia + "/" + mes + "/" + ano, DDMMYYYY);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // --- descrição / documento fiscal ------------------------------------------

    private static Optional<String> acharDescricao(List<String> linhas) {
        for (String linha : linhas) {
            Matcher m = ROTULO_BENEFICIARIO.matcher(linha);
            if (m.find()) {
                String valor = m.group(1).strip();
                if (valor.length() >= 3) {
                    return Optional.of(limitar(valor, 200));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> acharDocumentoFiscal(String texto) {
        Matcher chave = CHAVE_NFE.matcher(texto.replaceAll("\\s", ""));
        if (chave.find()) {
            return Optional.of(chave.group(1));
        }
        Matcher cnpj = CNPJ.matcher(texto);
        if (cnpj.find()) {
            return Optional.of(cnpj.group());
        }
        Matcher cpf = CPF.matcher(texto);
        if (cpf.find()) {
            return Optional.of(cpf.group());
        }
        return Optional.empty();
    }

    static BigDecimal brParaDecimal(String valorBr) {
        try {
            return new BigDecimal(valorBr.replace(".", "").replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String limitar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
