package br.com.unifinancas.infrastructure.ocr;

import br.com.unifinancas.domain.documento.TipoDocumento;
import java.util.Optional;

/**
 * Cliente do provedor de IA de visão usado como <b>último recurso</b> da
 * cascata de extração (ADR-016, etapa 4). Uma implementação por provedor
 * ({@link GeminiVisionClient}, {@link OpenAiVisionClient}); qual delas vira
 * bean é decidido por {@code unifinancas.ocr.ia.provedor} em
 * {@link VisaoIaConfig} (padrão: Gemini). Ambas mantidas para permitir a
 * troca sem mexer na cascata (ADR-006, Strategy da Seção 11).
 *
 * <p>Devolve vazio quando o modelo não produziu um valor parseável — a etapa
 * então "falha para dentro" e a cascata encerra sem sucesso (SAD Seção 7.6).
 */
interface ClienteVisaoDocumento {

    Optional<ExtracaoEtapa> extrair(byte[] imagemPng, TipoDocumento tipo);
}
