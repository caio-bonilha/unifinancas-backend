package br.com.unifinancas.infrastructure.ocr;

import br.com.unifinancas.application.documento.EntradaExtracao;
import br.com.unifinancas.application.documento.OrigemExtracao;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Etapa 1 da cascata (ADR-016): XML de NF-e/NFC-e enviado direto. Parsing
 * estruturado com StAX (JDK, sem dependência), custo zero e determinístico —
 * não há OCR envolvido. Não se aplica a boleto/cupom em imagem.
 *
 * <p>Lê os campos que interessam ao Lançamento: {@code vNF} (valor total da
 * nota), {@code dhEmi}/{@code dEmi} (emissão), {@code CNPJ} e {@code xNome} do
 * emitente, e a chave de acesso (44 dígitos).
 */
@Component
@Order(10)
class EtapaXmlNfe implements EtapaExtracao {

    private static final Logger log = LoggerFactory.getLogger(EtapaXmlNfe.class);

    @Override
    public boolean aceita(EntradaExtracao entrada) {
        String mime = entrada.mimeType() == null ? "" : entrada.mimeType().toLowerCase();
        String nome = entrada.nomeArquivo() == null ? "" : entrada.nomeArquivo().toLowerCase();
        if (mime.contains("xml") || nome.endsWith(".xml")) {
            return true;
        }
        String inicio = new String(entrada.conteudo(), 0, Math.min(entrada.conteudo().length, 200), StandardCharsets.UTF_8)
                .strip();
        return inicio.startsWith("<?xml") || inicio.contains("<NFe") || inicio.contains("<nfeProc");
    }

    @Override
    public Optional<ExtracaoEtapa> extrair(EntradaExtracao entrada) {
        String xml = new String(entrada.conteudo(), StandardCharsets.UTF_8);
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(entrada.conteudo()));

            BigDecimal valor = null;
            LocalDate emissao = null;
            String cnpjEmitente = null;
            String nomeEmitente = null;
            String chave = null;
            boolean dentroEmitente = false;
            boolean dentroTotalNota = false;

            while (reader.hasNext()) {
                int evento = reader.next();
                if (evento == XMLStreamConstants.START_ELEMENT) {
                    String tag = reader.getLocalName();
                    switch (tag) {
                        case "emit" -> dentroEmitente = true;
                        case "ICMSTot" -> dentroTotalNota = true;
                        case "vNF" -> {
                            if (dentroTotalNota && valor == null) {
                                valor = parseDecimal(reader.getElementText());
                            }
                        }
                        case "CNPJ" -> {
                            if (dentroEmitente && cnpjEmitente == null) {
                                cnpjEmitente = reader.getElementText();
                            }
                        }
                        case "xNome" -> {
                            if (dentroEmitente && nomeEmitente == null) {
                                nomeEmitente = reader.getElementText();
                            }
                        }
                        case "dhEmi", "dEmi" -> {
                            if (emissao == null) {
                                emissao = parseData(reader.getElementText());
                            }
                        }
                        case "infNFe", "infNFCe" -> chave = extrairChave(reader.getAttributeValue(null, "Id"));
                        default -> {
                            // ignora demais tags
                        }
                    }
                } else if (evento == XMLStreamConstants.END_ELEMENT) {
                    String tag = reader.getLocalName();
                    if ("emit".equals(tag)) {
                        dentroEmitente = false;
                    } else if ("ICMSTot".equals(tag)) {
                        dentroTotalNota = false;
                    }
                }
            }
            reader.close();

            if (valor == null && emissao == null && chave == null) {
                return Optional.empty();
            }
            return Optional.of(new ExtracaoEtapa(valor, null, emissao, nomeEmitente, chave != null ? chave : cnpjEmitente,
                    null, limitar(xml), null));
        } catch (Exception e) {
            log.warn("Falha ao ler XML de NF-e ({} bytes) - cascata segue.", entrada.conteudo().length, e);
            return Optional.empty();
        }
    }

    @Override
    public OrigemExtracao origem() {
        return OrigemExtracao.XML;
    }

    private static String extrairChave(String id) {
        if (id == null) {
            return null;
        }
        String digitos = id.replaceAll("\\D", "");
        return digitos.length() == 44 ? digitos : null;
    }

    private static BigDecimal parseDecimal(String s) {
        try {
            return new BigDecimal(s.strip());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalDate parseData(String s) {
        String v = s.strip();
        try {
            return OffsetDateTime.parse(v).toLocalDate();
        } catch (RuntimeException e) {
            try {
                return LocalDate.parse(v.length() >= 10 ? v.substring(0, 10) : v);
            } catch (RuntimeException e2) {
                return null;
            }
        }
    }

    private static String limitar(String s) {
        return s.length() <= 20_000 ? s : s.substring(0, 20_000);
    }
}
