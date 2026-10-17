package br.com.unifinancas.infrastructure.ocr;

import br.com.unifinancas.application.documento.EntradaExtracao;
import br.com.unifinancas.application.documento.OrigemExtracao;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Etapa 3 da cascata (ADR-016): OCR próprio para PDF escaneado e imagens
 * (foto de cupom fiscal, comprovante). A primeira página é rasterizada (PDF)
 * ou normalizada para PNG (imagem) e passada ao {@link TesseractRunner}
 * (subprocesso). O resultado só é aceito quando a confiança média fica acima
 * de {@code unifinancas.ocr.confianca-minima}; abaixo disso, ou se o binário
 * não estiver disponível, a cascata cai para a etapa OpenAI.
 */
@Component
@Order(30)
class EtapaOcrTesseract implements EtapaExtracao {

    private static final Logger log = LoggerFactory.getLogger(EtapaOcrTesseract.class);

    private final TesseractRunner tesseractRunner;
    private final RasterizadorDocumento rasterizador;
    private final double confiancaMinima;

    EtapaOcrTesseract(TesseractRunner tesseractRunner, RasterizadorDocumento rasterizador, OcrProperties propriedades) {
        this.tesseractRunner = tesseractRunner;
        this.rasterizador = rasterizador;
        this.confiancaMinima = propriedades.getConfiancaMinima();
    }

    @Override
    public boolean aceita(EntradaExtracao entrada) {
        String mime = entrada.mimeType() == null ? "" : entrada.mimeType().toLowerCase();
        String nome = entrada.nomeArquivo() == null ? "" : entrada.nomeArquivo().toLowerCase();
        return mime.startsWith("image/") || mime.contains("pdf")
                || nome.endsWith(".pdf") || nome.endsWith(".png") || nome.endsWith(".jpg") || nome.endsWith(".jpeg");
    }

    @Override
    public Optional<ExtracaoEtapa> extrair(EntradaExtracao entrada) {
        byte[] png;
        try {
            png = rasterizador.paraPng(entrada);
        } catch (IOException e) {
            log.warn("Não foi possível preparar a imagem para OCR - cascata segue.", e);
            return Optional.empty();
        }

        OcrResultado resultado;
        try {
            resultado = tesseractRunner.run(png);
        } catch (OcrIndisponivelException e) {
            log.warn("Etapa OCR indisponível ({}) - cascata segue para OpenAI.", e.getMessage());
            return Optional.empty();
        }

        if (resultado.confiancaMedia() < confiancaMinima) {
            log.info("OCR abaixo do limiar ({} < {}) - cascata segue para OpenAI.",
                    String.format("%.2f", resultado.confiancaMedia()), confiancaMinima);
            return Optional.empty();
        }

        ExtracaoEtapa extraido = ExtratorDeCampos.extrair(resultado.texto(), resultado.texto(),
                resultado.confiancaMedia());
        return extraido.temValor() ? Optional.of(extraido) : Optional.empty();
    }

    @Override
    public OrigemExtracao origem() {
        return OrigemExtracao.OCR;
    }
}
