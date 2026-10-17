package br.com.unifinancas.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.unifinancas.application.documento.EntradaExtracao;
import br.com.unifinancas.application.documento.OrigemExtracao;
import br.com.unifinancas.domain.documento.TipoDocumento;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EtapaXmlNfeTest {

    private final EtapaXmlNfe etapa = new EtapaXmlNfe();

    private static final String CHAVE = "35240812345678000190550010000012341123456789";

    private static final String NFE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <nfeProc xmlns="http://www.portalfiscal.inf.br/nfe">
              <NFe>
                <infNFe Id="NFe%s">
                  <ide><dhEmi>2026-08-25T10:30:00-03:00</dhEmi></ide>
                  <emit>
                    <CNPJ>12345678000190</CNPJ>
                    <xNome>ACME COMERCIO LTDA</xNome>
                  </emit>
                  <total>
                    <ICMSTot>
                      <vProd>1000.00</vProd>
                      <vNF>1234.56</vNF>
                    </ICMSTot>
                  </total>
                </infNFe>
              </NFe>
            </nfeProc>
            """.formatted(CHAVE);

    private EntradaExtracao entrada(byte[] conteudo, String mime, String nome) {
        return new EntradaExtracao(conteudo, mime, nome, TipoDocumento.NOTA_FISCAL, false);
    }

    @Test
    void aceitaPorMimeTypeXmlEPorConteudo() {
        assertThat(etapa.aceita(entrada("qualquer".getBytes(), "application/xml", "x.dat"))).isTrue();
        assertThat(etapa.aceita(entrada(NFE.getBytes(StandardCharsets.UTF_8), "application/octet-stream", "nota")))
                .isTrue();
        assertThat(etapa.aceita(entrada("%PDF-1.7".getBytes(), "application/pdf", "b.pdf"))).isFalse();
    }

    @Test
    void extraiValorEmissaoEmitenteEChave() {
        Optional<ExtracaoEtapa> r = etapa.extrair(
                entrada(NFE.getBytes(StandardCharsets.UTF_8), "application/xml", "nfe.xml"));

        assertThat(r).isPresent();
        ExtracaoEtapa e = r.get();
        assertThat(e.valor()).isEqualByComparingTo("1234.56");
        assertThat(e.emissao()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(e.descricao()).isEqualTo("ACME COMERCIO LTDA");
        assertThat(e.documentoFiscal()).isEqualTo(CHAVE);
        assertThat(e.textoBruto()).contains("<vNF>1234.56</vNF>");
        assertThat(etapa.origem()).isEqualTo(OrigemExtracao.XML);
    }

    @Test
    void xmlInvalidoResultaEmVazioSemLancar() {
        Optional<ExtracaoEtapa> r = etapa.extrair(
                entrada("<nfeProc><NFe>corrompido".getBytes(StandardCharsets.UTF_8), "application/xml", "x.xml"));

        assertThat(r).isEmpty();
    }
}
