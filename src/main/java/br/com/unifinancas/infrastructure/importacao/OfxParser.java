package br.com.unifinancas.infrastructure.importacao;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * RF-19, T4.1.1: extrai as transacoes de um arquivo de extrato bancario OFX.
 * Cobre as duas variantes do formato (ADR nao existe ainda para esta
 * decisao tecnica - nenhuma das duas exige escolha de negocio, so leitura
 * correta do arquivo que o banco exporta):
 * <ul>
 *   <li><b>OFX 1.x</b> (a maioria dos bancos brasileiros): SGML, sem tag de
 *   fechamento nos elementos-folha (ex.: {@code <DTPOSTED>20250115120000},
 *   sem {@code </DTPOSTED>}) e um cabecalho texto (nao-XML) antes da
 *   primeira tag ({@code OFXHEADER:100}, {@code DATA:OFXSGML}, ...).</li>
 *   <li><b>OFX 2.x</b>: XML valido, com um cabecalho {@code <?xml ...?>} /
 *   {@code <?OFX ...?>}.</li>
 * </ul>
 * Normaliza qualquer um dos dois para XML valido (fechando tags-folha linha
 * a linha - um no-op para quem ja vem fechado, como o 2.x) e le com
 * {@code DocumentBuilder} do proprio JDK, sem depender de nenhuma
 * biblioteca de terceiros so para isso.
 *
 * <p>So extrai - nao decide nada de negocio (deteccao de duplicidade,
 * sugestao de categoria) - isso e T4.1.3/T4.1.4, fora de escopo desta
 * tarefa.
 */
public final class OfxParser {

    private static final Pattern E_COMERCIAL_SOLTO = Pattern.compile("&(?!(?:amp|lt|gt|quot|apos|#\\d+|#x[0-9a-fA-F]+);)");
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    private OfxParser() {
    }

    /** Detecta o {@code CHARSET} declarado no cabecalho (1252/8859-1/UTF-8, default UTF-8) antes de decodificar os bytes. */
    public static ExtratoOfx parse(byte[] conteudo) {
        String cabecalho = new String(conteudo, StandardCharsets.ISO_8859_1);
        Charset charset = charsetDoCabecalho(cabecalho);
        return parse(new String(conteudo, charset));
    }

    public static ExtratoOfx parse(String conteudoOfx) {
        try {
            String xml = normalizarParaXml(conteudoOfx);
            Document documento = paraDocumento(xml);
            return extrairExtrato(documento);
        } catch (OfxParseException e) {
            throw e;
        } catch (Exception e) {
            throw new OfxParseException("Nao foi possivel interpretar o arquivo OFX: " + e.getMessage(), e);
        }
    }

    private static Charset charsetDoCabecalho(String cabecalho) {
        for (String linha : cabecalho.split("\r?\n", 30)) {
            if (linha.indexOf('<') >= 0) {
                break; // saiu do cabecalho texto e entrou no corpo OFX
            }
            String[] partes = linha.split(":", 2);
            if (partes.length == 2 && "CHARSET".equalsIgnoreCase(partes[0].trim())) {
                return switch (partes[1].trim().toUpperCase(Locale.ROOT)) {
                    case "1252" -> Charset.forName("windows-1252");
                    case "8859-1", "ISO-8859-1" -> StandardCharsets.ISO_8859_1;
                    default -> StandardCharsets.UTF_8;
                };
            }
        }
        return StandardCharsets.UTF_8;
    }

    /** Corta o cabecalho texto (OFX 1.x) na primeira tag, fecha elementos-folha linha a linha, e escapa "&" solto. */
    private static String normalizarParaXml(String conteudoOfx) {
        int inicio = conteudoOfx.indexOf('<');
        if (inicio < 0) {
            throw new OfxParseException("Arquivo nao contem nenhuma tag OFX ('<').");
        }
        StringBuilder normalizado = new StringBuilder();
        for (String linha : conteudoOfx.substring(inicio).split("\r?\n")) {
            String processada = normalizarLinha(linha);
            if (!processada.isEmpty()) {
                normalizado.append(processada).append('\n');
            }
        }
        return E_COMERCIAL_SOLTO.matcher(normalizado).replaceAll("&amp;");
    }

    /**
     * Fecha uma tag-folha SGML (ex.: {@code <TRNAMT>-45.90} vira
     * {@code <TRNAMT>-45.90</TRNAMT>}). Tags de container (nada depois do
     * {@code >} na mesma linha), tags ja fechadas (2.x) e processing
     * instructions ({@code <?xml ...?>}, {@code <?OFX ...?>}) passam
     * inalteradas - todas caem no caso "resto vazio ou ja fechado".
     */
    private static String normalizarLinha(String linhaOriginal) {
        String linha = linhaOriginal.trim();
        if (linha.isEmpty() || linha.startsWith("</") || !linha.startsWith("<")) {
            return linha;
        }
        int fechaTag = linha.indexOf('>');
        if (fechaTag < 0) {
            return linha; // malformado - deixa o parser XML acusar
        }
        String tag = linha.substring(1, fechaTag);
        if (tag.isEmpty() || tag.startsWith("/") || tag.startsWith("?")) {
            return linha;
        }
        String resto = linha.substring(fechaTag + 1);
        if (resto.isEmpty() || resto.startsWith("<") || resto.endsWith("</" + tag + ">")) {
            return linha;
        }
        return linha + "</" + tag + ">";
    }

    private static Document paraDocumento(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Protecao contra XXE (RSeg-*, boa pratica padrao) - OFX nunca declara DOCTYPE nem entidades externas legitimamente.
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document documento = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        documento.getDocumentElement().normalize();
        if (!"OFX".equalsIgnoreCase(documento.getDocumentElement().getTagName())) {
            throw new OfxParseException("Arquivo nao e um OFX valido (elemento raiz esperado <OFX>).");
        }
        return documento;
    }

    private static ExtratoOfx extrairExtrato(Document documento) {
        Element conta = primeiroElemento(documento, "BANKACCTFROM");
        if (conta == null) {
            conta = primeiroElemento(documento, "CCACCTFROM");
        }
        String instituicaoId = conta != null ? textoFilho(conta, "BANKID") : null;
        String numeroConta = conta != null ? textoFilho(conta, "ACCTID") : null;

        Element curdef = primeiroElemento(documento, "CURDEF");
        String moeda = curdef != null ? curdef.getTextContent().trim() : null;

        LocalDate dataInicio = parseDataOuNulo(textoDoPrimeiro(documento, "DTSTART"));
        LocalDate dataFim = parseDataOuNulo(textoDoPrimeiro(documento, "DTEND"));

        List<TransacaoOfx> transacoes = new ArrayList<>();
        NodeList stmttrn = documento.getElementsByTagName("STMTTRN");
        for (int i = 0; i < stmttrn.getLength(); i++) {
            transacoes.add(extrairTransacao((Element) stmttrn.item(i)));
        }

        return new ExtratoOfx(instituicaoId, numeroConta, moeda, dataInicio, dataFim, transacoes);
    }

    private static TransacaoOfx extrairTransacao(Element stmttrn) {
        String tipoBruto = textoFilho(stmttrn, "TRNTYPE");
        TipoTransacaoOfx tipo = paraTipoTransacao(tipoBruto);

        String dataBruta = textoFilho(stmttrn, "DTPOSTED");
        if (dataBruta == null) {
            throw new OfxParseException("STMTTRN sem DTPOSTED.");
        }
        LocalDate data = parseData(dataBruta);

        String valorBruto = textoFilho(stmttrn, "TRNAMT");
        if (valorBruto == null) {
            throw new OfxParseException("STMTTRN sem TRNAMT.");
        }
        BigDecimal valor = parseValor(valorBruto);

        String identificadorExterno = textoFilho(stmttrn, "FITID");
        String nome = textoFilho(stmttrn, "NAME");
        String memo = textoFilho(stmttrn, "MEMO");
        String descricao = combinarDescricao(nome, memo);

        return new TransacaoOfx(identificadorExterno, data, valor, tipo, descricao);
    }

    private static String combinarDescricao(String nome, String memo) {
        if (nome != null && memo != null && !nome.equals(memo)) {
            return nome + " - " + memo;
        }
        if (nome != null) {
            return nome;
        }
        return memo != null ? memo : "";
    }

    private static TipoTransacaoOfx paraTipoTransacao(String bruto) {
        if (bruto == null) {
            return TipoTransacaoOfx.OTHER;
        }
        try {
            return TipoTransacaoOfx.valueOf(bruto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TipoTransacaoOfx.OTHER;
        }
    }

    private static LocalDate parseData(String bruta) {
        String soDigitos = bruta.trim();
        if (soDigitos.length() < 8) {
            throw new OfxParseException("Data OFX invalida: '" + bruta + "'.");
        }
        try {
            return LocalDate.parse(soDigitos.substring(0, 8), FORMATO_DATA);
        } catch (Exception e) {
            throw new OfxParseException("Data OFX invalida: '" + bruta + "'.", e);
        }
    }

    private static LocalDate parseDataOuNulo(String bruta) {
        return bruta == null ? null : parseData(bruta);
    }

    private static BigDecimal parseValor(String bruto) {
        try {
            return new BigDecimal(bruto.trim());
        } catch (NumberFormatException e) {
            throw new OfxParseException("Valor OFX invalido: '" + bruto + "'.", e);
        }
    }

    private static Element primeiroElemento(Document documento, String tag) {
        NodeList lista = documento.getElementsByTagName(tag);
        return lista.getLength() > 0 ? (Element) lista.item(0) : null;
    }

    private static String textoDoPrimeiro(Document documento, String tag) {
        Element elemento = primeiroElemento(documento, tag);
        return elemento != null ? elemento.getTextContent().trim() : null;
    }

    /** Texto do primeiro descendente direto (nao recursivo) de {@code pai} com o nome de tag informado. */
    private static String textoFilho(Element pai, String tag) {
        NodeList filhos = pai.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            Node filho = filhos.item(i);
            if (filho.getNodeType() == Node.ELEMENT_NODE && tag.equalsIgnoreCase(filho.getNodeName())) {
                return filho.getTextContent().trim();
            }
        }
        return null;
    }
}
