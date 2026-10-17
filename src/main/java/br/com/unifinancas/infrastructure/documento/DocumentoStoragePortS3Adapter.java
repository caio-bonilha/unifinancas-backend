package br.com.unifinancas.infrastructure.documento;

import br.com.unifinancas.application.documento.DocumentoStoragePort;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/** Implementacao real de {@link DocumentoStoragePort} via Amazon S3 (T1.8.2, decisao consultada com o usuario). */
@Component
public class DocumentoStoragePortS3Adapter implements DocumentoStoragePort {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration validadeUrlAssinada;

    public DocumentoStoragePortS3Adapter(S3Client s3Client, S3Presigner s3Presigner,
            @Value("${unifinancas.storage.s3.bucket}") String bucket,
            @Value("${unifinancas.storage.s3.url-assinada-validade-minutos}") long validadeMinutos) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.validadeUrlAssinada = Duration.ofMinutes(validadeMinutos);
    }

    @Override
    public void armazenar(String chave, byte[] conteudo, String mimeType) {
        PutObjectRequest request = PutObjectRequest.builder().bucket(bucket).key(chave).contentType(mimeType).build();
        s3Client.putObject(request, RequestBody.fromBytes(conteudo));
    }

    @Override
    public byte[] baixar(String chave) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(chave).build()).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new IllegalStateException("Objeto nao encontrado no Object Storage: " + chave, e);
        }
    }

    @Override
    public void remover(String chave) {
        // S3 DeleteObject ja e idempotente: apagar uma chave inexistente retorna 204.
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(chave).build());
    }

    @Override
    public String gerarUrlAssinada(String chave) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucket).key(chave).build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(validadeUrlAssinada)
                .getObjectRequest(getObjectRequest)
                .build();
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }
}
