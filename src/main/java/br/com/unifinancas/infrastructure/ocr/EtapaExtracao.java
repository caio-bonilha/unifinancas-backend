package br.com.unifinancas.infrastructure.ocr;

import br.com.unifinancas.application.documento.EntradaExtracao;
import br.com.unifinancas.application.documento.OrigemExtracao;
import java.util.Optional;

/**
 * Uma etapa da cadeia de responsabilidade da cascata de extração (ADR-016).
 * O {@link ProcessamentoDocumentoCascataAdapter} percorre as etapas em ordem
 * fixa (XML → PDFBox → OCR → OpenAI) e para na primeira que devolve um
 * resultado com {@code valor}.
 *
 * <p>"Falhar para dentro" (SAD Seção 7.6): quando uma etapa não se aplica ao
 * arquivo, não consegue ler nada, ou lê sem achar um valor, ela devolve
 * {@link Optional#empty()} e a cascata segue para a próxima — não lança.
 * Exceção: a etapa OpenAI aplica resiliência própria e, esgotada, também
 * devolve vazio (o adapter então reporta falha).
 */
interface EtapaExtracao {

    /** Esta etapa faz sentido para este arquivo? (ex.: PDFBox só para PDF). */
    boolean aceita(EntradaExtracao entrada);

    /** Tenta extrair; vazio = "não consegui, tente a próxima etapa". */
    Optional<ExtracaoEtapa> extrair(EntradaExtracao entrada);

    OrigemExtracao origem();
}
