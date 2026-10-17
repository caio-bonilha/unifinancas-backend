package br.com.unifinancas.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.unifinancas.application.documento.EntradaExtracao;
import br.com.unifinancas.domain.documento.TipoDocumento;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class EtapaPdfBoxTest {

    private final EtapaPdfBox etapa = new EtapaPdfBox();

    private static final String LINHA_47 = "237933812860082665995090000633050" + "1565" + "0000026035";

    private EntradaExtracao entrada(byte[] conteudo) {
        return new EntradaExtracao(conteudo, "application/pdf", "boleto.pdf", TipoDocumento.BOLETO, false);
    }

    private static byte[] pdfComTexto(List<String> linhas) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.setLeading(14);
                cs.newLineAtOffset(50, 750);
                for (String linha : linhas) {
                    cs.showText(linha);
                    cs.newLine();
                }
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] pdfSemTexto() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void aceitaApenasPdf() {
        assertThat(etapa.aceita(entrada(new byte[] {1}))).isTrue();
        assertThat(etapa.aceita(new EntradaExtracao(new byte[] {1}, "image/png", "x.png",
                TipoDocumento.COMPROVANTE, false))).isFalse();
    }

    @Test
    void leCamadaDeTextoDoPdfEExtraiCampos() throws IOException {
        byte[] pdf = pdfComTexto(List.of(
                "Banco Exemplo S.A.",
                "Beneficiario: ENEL DISTRIBUICAO SP",
                LINHA_47,
                "Vencimento: 10/09/2026",
                "Valor do documento: 260,35"));

        Optional<ExtracaoEtapa> r = etapa.extrair(entrada(pdf));

        assertThat(r).isPresent();
        assertThat(r.get().valor()).isEqualByComparingTo("260.35");
        assertThat(r.get().vencimento()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(r.get().linhaDigitavel()).isEqualTo(LINHA_47);
        assertThat(r.get().confiancaOcr()).isNull();
    }

    @Test
    void pdfEscaneadoSemTextoResultaEmVazioParaCairNoOcr() throws IOException {
        assertThat(etapa.extrair(entrada(pdfSemTexto()))).isEmpty();
    }

    @Test
    void pdfComTextoMasSemValorResultaEmVazio() throws IOException {
        byte[] pdf = pdfComTexto(List.of("Comprovante de agendamento", "Protocolo 998877"));

        assertThat(etapa.extrair(entrada(pdf))).isEmpty();
    }
}
