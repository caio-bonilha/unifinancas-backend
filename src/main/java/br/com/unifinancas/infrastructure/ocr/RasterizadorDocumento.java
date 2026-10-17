package br.com.unifinancas.infrastructure.ocr;

import br.com.unifinancas.application.documento.EntradaExtracao;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

/**
 * Converte a primeira página/imagem de um documento em PNG, para as etapas
 * que trabalham com pixels (OCR próprio e OpenAI visão — ADR-016, etapas 3 e
 * 4). PDF é rasterizado com o PDFBox no DPI configurado; imagem é
 * re-encodada como PNG (normaliza JPG etc.).
 */
@Component
class RasterizadorDocumento {

    private final int dpi;

    RasterizadorDocumento(OcrProperties propriedades) {
        this.dpi = propriedades.getTesseract().getDpi();
    }

    byte[] paraPng(EntradaExtracao entrada) throws IOException {
        String mime = entrada.mimeType() == null ? "" : entrada.mimeType().toLowerCase();
        String nome = entrada.nomeArquivo() == null ? "" : entrada.nomeArquivo().toLowerCase();
        boolean pdf = mime.contains("pdf") || nome.endsWith(".pdf");
        if (pdf) {
            try (PDDocument documento = Loader.loadPDF(entrada.conteudo())) {
                return paraPngBytes(new PDFRenderer(documento).renderImageWithDPI(0, dpi));
            }
        }
        BufferedImage imagem = ImageIO.read(new ByteArrayInputStream(entrada.conteudo()));
        if (imagem == null) {
            // formato que o ImageIO não decodifica; devolve os bytes crus.
            return entrada.conteudo();
        }
        return paraPngBytes(imagem);
    }

    private static byte[] paraPngBytes(BufferedImage imagem) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(imagem, "png", baos);
        return baos.toByteArray();
    }
}
