package br.com.unifinancas.infrastructure.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prompt e parsing compartilhados pelos clientes de IA de visão (ADR-016,
 * etapa 4) — o que muda entre Gemini e OpenAI é só o protocolo HTTP; o
 * contrato de campos pedido ao modelo e a leitura da resposta são idênticos.
 */
final class RespostaIaParser {

    private static final Logger log = LoggerFactory.getLogger(RespostaIaParser.class);

    static final String PROMPT_SISTEMA = """
            Você extrai dados de documentos financeiros brasileiros (boleto, cupom fiscal, \
            nota fiscal, comprovante). Responda SOMENTE com um objeto JSON, sem texto ao redor, \
            com as chaves: valor (string decimal com ponto, ex. "1234.56", ou null), \
            vencimento (data "AAAA-MM-DD" ou null), emissao (data "AAAA-MM-DD" ou null), \
            descricao (beneficiário/emitente/estabelecimento, ou null), \
            documentoFiscal (CNPJ/CPF ou chave de NF-e, ou null), \
            linhaDigitavel (47 dígitos do boleto, sem separadores, ou null).""";

    private RespostaIaParser() {
    }

    static String instrucaoDoTipo(String tipoDocumento) {
        return "Tipo declarado pelo usuário: " + tipoDocumento + ". Extraia os campos.";
    }

    /** Converte o texto JSON devolvido pelo modelo em {@link ExtracaoEtapa}; vazio se não parsear. */
    static Optional<ExtracaoEtapa> parse(String conteudoModelo, ObjectMapper objectMapper) {
        if (conteudoModelo == null || conteudoModelo.isBlank()) {
            return Optional.empty();
        }
        String json = conteudoModelo.strip();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```(?:json)?", "").replaceAll("```$", "").strip();
        }
        try {
            JsonNode n = objectMapper.readTree(json);
            String valorTexto = texto(n, "valor");
            BigDecimal valor = valorTexto == null ? null : new BigDecimal(valorTexto);
            return Optional.of(new ExtracaoEtapa(
                    valor,
                    data(n, "vencimento"),
                    data(n, "emissao"),
                    texto(n, "descricao"),
                    texto(n, "documentoFiscal"),
                    texto(n, "linhaDigitavel"),
                    conteudoModelo,
                    null));
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Resposta da IA de visão não é JSON parseável: {}", conteudoModelo, e);
            return Optional.empty();
        }
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.get(campo);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText().strip();
        return s.isBlank() || s.equalsIgnoreCase("null") ? null : s;
    }

    private static LocalDate data(JsonNode n, String campo) {
        String s = texto(n, campo);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
