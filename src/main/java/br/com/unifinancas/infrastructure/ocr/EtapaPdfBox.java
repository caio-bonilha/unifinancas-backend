package br.com.unifinancas.infrastructure.ocr;

import br.com.unifinancas.application.documento.EntradaExtracao;
import br.com.unifinancas.application.documento.OrigemExtracao;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Etapa 2 da cascata (ADR-016): PDF com camada de texto — caso típico de
 * boleto emitido por internet banking. O PDFBox lê o texto e o
 * {@link ExtratorDeCampos} resolve os campos (linha digitável FEBRABAN,
 * valor, vencimento, beneficiário). Custo zero.
 *
 * <p>PDF <b>escaneado</b> (sem camada de texto) devolve vazio aqui — a
 * cascata segue para o OCR próprio (etapa 3).
 */
@Component
@Order(20)
class EtapaPdfBox implements EtapaExtracao {

    private static final Logger log = LoggerFactory.getLogger(EtapaPdfBox.class);

    @Override
    public boolean aceita(EntradaExtracao entrada) {
        String mime = entrada.mimeType() == null ? "" : entrada.mimeType().toLowerCase();
        String nome = entrada.nomeArquivo() == null ? "" : entrada.nomeArquivo().toLowerCase();
        return mime.contains("pdf") || nome.endsWith(".pdf");
    }

    @Override
    public Optional<ExtracaoEtapa> extrair(EntradaExtracao entrada) {
        try (PDDocument documento = Loader.loadPDF(entrada.conteudo())) {
            String texto = new PDFTextStripper().getText(documento);
            if (texto == null || texto.isBlank()) {
                return Optional.empty();
            }
            ExtracaoEtapa extraido = ExtratorDeCampos.extrair(texto, texto, null);
            return extraido.temValor() ? Optional.of(extraido) : Optional.empty();
        } catch (Exception e) {
            log.warn("Falha ao ler PDF com PDFBox ({} bytes) - cascata segue para OCR.", entrada.conteudo().length, e);
            return Optional.empty();
        }
    }

    @Override
    public OrigemExtracao origem() {
        return OrigemExtracao.PDFBOX;
    }
}
