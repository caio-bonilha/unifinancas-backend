package br.com.unifinancas.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Só o parse do TSV do Tesseract (sem o binário) — o que a imagem Docker do
 * backend roda de verdade fica para teste manual/deploy (ADR-016).
 */
class TesseractRunnerTsvTest {

    private static final String CABECALHO =
            "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext";

    @Test
    void mediaDeConfiancaIgnoraLinhasEstruturais() {
        String tsv = CABECALHO + "\n"
                + "4\t1\t1\t1\t1\t0\t0\t0\t0\t0\t-1\t\n"          // linha estrutural (conf -1)
                + "5\t1\t1\t1\t1\t1\t10\t10\t30\t12\t90\tValor\n"
                + "5\t1\t1\t1\t1\t2\t50\t10\t40\t12\t80\t1.234,56\n"
                + "5\t1\t1\t1\t2\t1\t10\t30\t60\t12\t70\tVencimento\n";

        OcrResultado r = TesseractRunner.parseTsv(tsv);

        assertThat(r.confiancaMedia()).isCloseTo(0.80, within(0.001)); // (90+80+70)/3 / 100
        assertThat(r.texto()).isEqualTo("Valor 1.234,56\nVencimento");
    }

    @Test
    void tsvSemPalavrasReconhecidasDaConfiancaZero() {
        String tsv = CABECALHO + "\n"
                + "4\t1\t1\t1\t1\t0\t0\t0\t0\t0\t-1\t\n"
                + "5\t1\t1\t1\t1\t1\t0\t0\t0\t0\t-1\t   \n";

        OcrResultado r = TesseractRunner.parseTsv(tsv);

        assertThat(r.confiancaMedia()).isZero();
        assertThat(r.texto()).isEmpty();
    }

    @Test
    void linhasDeAvisoDoTesseractSemTabSaoIgnoradas() {
        String tsv = "Warning: Invalid resolution 0 dpi. Using 70 instead.\n"
                + CABECALHO + "\n"
                + "5\t1\t1\t1\t1\t1\t10\t10\t30\t12\t95\tTOTAL\n";

        OcrResultado r = TesseractRunner.parseTsv(tsv);

        assertThat(r.confiancaMedia()).isCloseTo(0.95, within(0.001));
        assertThat(r.texto()).isEqualTo("TOTAL");
    }
}
