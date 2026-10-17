package br.com.unifinancas.application.documento;

/**
 * Porta para o Object Storage (T1.8.2, SAD Sec8.4/Sec9.1) - decisao de
 * negocio resolvida com o usuario: Amazon S3. {@code chave} e o caminho do
 * objeto dentro do bucket (equivalente ao {@code arquivoUrl} de {@link
 * br.com.unifinancas.domain.documento.Documento}, T1.8.1); nunca uma URL
 * publica - o bucket nao tem acesso publico direto, so URLs assinadas de
 * curta duracao geradas sob demanda por {@link #gerarUrlAssinada}.
 */
public interface DocumentoStoragePort {

    void armazenar(String chave, byte[] conteudo, String mimeType);

    String gerarUrlAssinada(String chave);

    /**
     * Baixa o conteúdo do objeto. Usado pelo worker de processamento de
     * documento (T3.1.4, Fluxo 6.3) para reler o arquivo e passá-lo à cascata
     * de extração — o worker é assíncrono e não tem os bytes do upload.
     */
    byte[] baixar(String chave);

    /**
     * `[ADR-020]` Apaga o objeto em definitivo. Usado só pela purga
     * assíncrona (`PurgaDocumentoJob`), depois da janela de retenção — a
     * exclusão pela UI é soft-delete e não toca no Object Storage. Idempotente:
     * apagar uma chave que já não existe não é erro.
     */
    void remover(String chave);
}
