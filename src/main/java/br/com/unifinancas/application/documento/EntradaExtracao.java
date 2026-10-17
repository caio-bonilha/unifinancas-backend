package br.com.unifinancas.application.documento;

import br.com.unifinancas.domain.documento.TipoDocumento;

/**
 * Tudo o que a cascata de extração (ADR-016) precisa para processar um
 * documento, montado pelo {@code ProcessamentoDocumentoOrchestrator} (T3.1.4)
 * a partir do {@link br.com.unifinancas.domain.documento.Documento} e do
 * arquivo baixado do Object Storage.
 *
 * <p>{@code fallbackIaHabilitado} é a flag {@code ocrFallbackIaHabilitado} do
 * Ambiente Financeiro (T3.1.2, ADR-016) — o orquestrador a resolve e a passa
 * pronta, para o adapter de infraestrutura nunca precisar tocar em repositório
 * de domínio.
 *
 * @param conteudo            bytes do arquivo (PDF, imagem ou XML)
 * @param mimeType            tipo MIME informado no upload
 * @param nomeArquivo         nome original do arquivo (usado como dica de formato)
 * @param tipo                tipo do documento declarado pelo usuário no upload
 * @param fallbackIaHabilitado se o Ambiente Financeiro permite a etapa OpenAI
 */
public record EntradaExtracao(
        byte[] conteudo,
        String mimeType,
        String nomeArquivo,
        TipoDocumento tipo,
        boolean fallbackIaHabilitado) {
}
