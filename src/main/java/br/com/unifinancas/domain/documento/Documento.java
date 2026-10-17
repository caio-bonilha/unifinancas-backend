package br.com.unifinancas.domain.documento;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Arquivo anexado ou usado como fonte de dados (boleto, cupom fiscal, nota
 * fiscal, comprovante) - RF-16, SAD Sec4.14. Mapeia a tabela {@code
 * documento} (T1.8.1) por inteiro. Apenas a entidade de dominio nesta
 * tarefa - integracao com Object Storage fica para a T1.8.2, e o endpoint
 * de upload/anexo para a T1.8.3 (sem {@code ProcessamentoDocumento} ainda -
 * isso e Fase 3, Sec4.15).
 *
 * <p>{@code arquivoUrl} guarda a chave/caminho do objeto no Object Storage,
 * nao uma URL publica permanente - o bucket nao tem acesso publico direto
 * (Sec8.4); quem le uma URL assinada de curta duracao gera-a sob demanda a
 * partir dessa chave (T1.8.2).
 */
@Entity
@Table(name = "documento")
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDocumento tipo;

    @Column(name = "arquivo_url", nullable = false, length = 500)
    private String arquivoUrl;

    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "enviado_por_usuario_id", nullable = false)
    private UUID enviadoPorUsuarioId;

    /**
     * `[Revisado - Etapa B]` Lado "dono" do vinculo Documento -> Lancamento
     * (N:1): varios Documentos podem apontar para o mesmo Lancamento; cada
     * Documento pertence a no maximo um. {@code null} = documento avulso
     * (ex.: recem-enviado para processamento, ainda sem Lancamento).
     */
    @Column(name = "lancamento_id")
    private UUID lancamentoId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    /**
     * `[ADR-020]` Soft-delete: quando != null, o Documento foi excluido e some
     * de todas as listagens. Continua na tabela (e o arquivo no Object Storage)
     * ate a purga assincrona levar embora em definitivo, apos a janela de
     * retencao. {@code null} = Documento ativo.
     */
    @Column(name = "excluido_em")
    private Instant excluidoEm;

    @Column(name = "excluido_por_usuario_id")
    private UUID excluidoPorUsuarioId;

    protected Documento() {
        // exigido pelo JPA
    }

    public static Documento novo(UUID ambienteFinanceiroId, TipoDocumento tipo, String arquivoUrl, String nomeArquivo,
            long tamanhoBytes, String mimeType, UUID enviadoPorUsuarioId) {
        validarTexto(arquivoUrl, "ARQUIVO_URL_OBRIGATORIO", "arquivoUrl e obrigatoria.");
        validarTexto(nomeArquivo, "NOME_ARQUIVO_OBRIGATORIO", "nomeArquivo e obrigatorio.");
        validarTexto(mimeType, "MIME_TYPE_OBRIGATORIO", "mimeType e obrigatorio.");
        validarTamanho(tamanhoBytes);

        Documento documento = new Documento();
        documento.ambienteFinanceiroId = ambienteFinanceiroId;
        documento.tipo = tipo;
        documento.arquivoUrl = arquivoUrl;
        documento.nomeArquivo = nomeArquivo;
        documento.tamanhoBytes = tamanhoBytes;
        documento.mimeType = mimeType;
        documento.enviadoPorUsuarioId = enviadoPorUsuarioId;
        documento.criadoEm = Instant.now();
        return documento;
    }

    /**
     * `[Revisado - Etapa B]` Vincula este Documento a um Lancamento (anexo da
     * T1.8.3 ou confirmacao de documento processado da T3.1.5). Sobrescreve o
     * vinculo anterior, se houver.
     */
    public void vincularALancamento(UUID lancamentoId) {
        this.lancamentoId = lancamentoId;
    }

    /**
     * `[Revisado - Etapa B]` Desfaz o vinculo com o Lancamento sem excluir o
     * Documento — o arquivo continua no Object Storage e listavel na central de
     * revisao. Excluir de fato e {@link #excluir(UUID)}.
     */
    public void desvincularDoLancamento() {
        this.lancamentoId = null;
    }

    /**
     * `[ADR-020]` Soft-delete: o Documento some das listagens no ato; a purga
     * assincrona (PurgaDocumentoJob) apaga em definitivo apos a janela de
     * retencao. Zera o vinculo com o Lancamento antes de marcar ("desvincular e
     * excluir") — nao mexe no Lancamento em si. Idempotencia nao e permitida:
     * excluir de novo e erro (a UI ja tira o Documento da tela).
     */
    public void excluir(UUID usuarioId) {
        if (usuarioId == null) {
            throw new RegraDeNegocioException("USUARIO_OBRIGATORIO", "usuarioId e obrigatorio para excluir.");
        }
        if (excluidoEm != null) {
            throw new RegraDeNegocioException("DOCUMENTO_JA_EXCLUIDO", "Documento ja foi excluido.");
        }
        this.lancamentoId = null;
        this.excluidoEm = Instant.now();
        this.excluidoPorUsuarioId = usuarioId;
    }

    public boolean isExcluido() {
        return excluidoEm != null;
    }

    private static void validarTexto(String valor, String codigo, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new RegraDeNegocioException(codigo, mensagem);
        }
    }

    private static void validarTamanho(long tamanhoBytes) {
        if (tamanhoBytes <= 0) {
            throw new RegraDeNegocioException("TAMANHO_INVALIDO", "tamanhoBytes deve ser maior que zero.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getAmbienteFinanceiroId() {
        return ambienteFinanceiroId;
    }

    public TipoDocumento getTipo() {
        return tipo;
    }

    public String getArquivoUrl() {
        return arquivoUrl;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public String getMimeType() {
        return mimeType;
    }

    public UUID getEnviadoPorUsuarioId() {
        return enviadoPorUsuarioId;
    }

    public UUID getLancamentoId() {
        return lancamentoId;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getExcluidoEm() {
        return excluidoEm;
    }

    public UUID getExcluidoPorUsuarioId() {
        return excluidoPorUsuarioId;
    }
}
