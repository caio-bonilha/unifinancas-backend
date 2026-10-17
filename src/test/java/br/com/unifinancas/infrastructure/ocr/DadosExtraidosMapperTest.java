package br.com.unifinancas.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.unifinancas.application.documento.OrigemExtracao;
import br.com.unifinancas.domain.documento.TipoDocumento;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DadosExtraidosMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DadosExtraidosMapper mapper = new DadosExtraidosMapper(objectMapper);

    private JsonNode json(ExtracaoEtapa etapa, TipoDocumento tipo, OrigemExtracao origem) throws Exception {
        return objectMapper.readTree(mapper.toJson(etapa, tipo, origem));
    }

    @Test
    void serializaCamposComOrigemEValorComoStringDecimal() throws Exception {
        ExtracaoEtapa etapa = new ExtracaoEtapa(new BigDecimal("260.35"), LocalDate.of(2026, 9, 10), null,
                "ENEL SP", null, "0001", "texto do boleto", null);

        JsonNode n = json(etapa, TipoDocumento.BOLETO, OrigemExtracao.PDFBOX);

        assertThat(n.get("origemExtracao").asText()).isEqualTo("PDFBOX");
        assertThat(n.get("valor").asText()).isEqualTo("260.35");
        assertThat(n.get("vencimento").asText()).isEqualTo("2026-09-10");
        assertThat(n.get("emissao").isNull()).isTrue();
        assertThat(n.get("tipoDocumento").asText()).isEqualTo("BOLETO");
        assertThat(n.get("textoBruto").asText()).isEqualTo("texto do boleto");
        assertThat(n.get("confiancaOcr").isNull()).isTrue();
    }

    @Test
    void camposFaltandoDeBoletoSemVencimentoNemLinhaDigitavel() throws Exception {
        ExtracaoEtapa etapa = new ExtracaoEtapa(new BigDecimal("50.00"), null, null, null, null, null, "t", null);

        JsonNode n = json(etapa, TipoDocumento.BOLETO, OrigemExtracao.OCR);

        assertThat(n.get("camposFaltando")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("descricao", "vencimento", "linhaDigitavel");
    }

    @Test
    void camposFaltandoDeNotaFiscalCobraEmissaoEDocumentoFiscal() throws Exception {
        ExtracaoEtapa etapa = new ExtracaoEtapa(new BigDecimal("10.00"), null, null, "ACME", null, null, "t", null);

        JsonNode n = json(etapa, TipoDocumento.NOTA_FISCAL, OrigemExtracao.XML);

        assertThat(n.get("camposFaltando")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("emissao", "documentoFiscal");
    }

    @Test
    void camposFaltandoIncluiValorQuandoAusente() throws Exception {
        ExtracaoEtapa etapa = new ExtracaoEtapa(null, null, LocalDate.of(2026, 8, 25), "Loja", null, null, "t", 0.9);

        JsonNode n = json(etapa, TipoDocumento.CUPOM_FISCAL, OrigemExtracao.IA);

        assertThat(n.get("camposFaltando")).extracting(JsonNode::asText).contains("valor");
        assertThat(n.get("confiancaOcr").asDouble()).isEqualTo(0.9);
    }

    @Test
    void comprovanteComAlgumaDataNaoAcusaDataFaltando() throws Exception {
        ExtracaoEtapa etapa = new ExtracaoEtapa(new BigDecimal("300.00"), LocalDate.of(2026, 9, 1), null,
                "Transferencia PIX", null, null, "t", null);

        JsonNode n = json(etapa, TipoDocumento.COMPROVANTE, OrigemExtracao.PDFBOX);

        assertThat(n.get("camposFaltando")).extracting(JsonNode::asText).doesNotContain("data", "valor");
    }
}
