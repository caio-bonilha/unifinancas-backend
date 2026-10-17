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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registro do pipeline de OCR/IA sobre um {@link Documento} - SAD Sec4.15,
 * ADR-016. Um Documento tem no maximo um ProcessamentoDocumento
 * ({@code documento_id} unico). Mapeia a tabela {@code processamento_documento}
 * (T3.1.2) por inteiro.
 *
 * <p>Apenas a entidade e as transicoes de estado nesta tarefa. Quem chama as
 * transicoes e o {@code ProcessamentoDocumentoOrchestrator} (worker assincrono,
 * T3.1.4, Fluxo 6.3); a extracao em si vem do adapter da cascata XML -> PDFBox
 * -> Tesseract -> OpenAI (T3.1.3). A confirmacao ({@link #confirmar}) e
 * disparada pelo endpoint de revisao (T3.1.5) - nunca automaticamente (RN-08).
 *
 * <p>{@code dadosExtraidos} guarda o JSON estruturado produzido pela cascata,
 * no mesmo formato que o passo de revisao humana edita; o schema e livre
 * (coluna {@code jsonb}), definido em T3.1.3/T3.1.4.
 */
@Entity
@Table(name = "processamento_documento")
public class ProcessamentoDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    @Column(name = "documento_id", nullable = false)
    private UUID documentoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusProcessamento status;

    @Column(name = "dados_extraidos")
    @JdbcTypeCode(SqlTypes.JSON)
    private String dadosExtraidos;

    @Column(name = "erro_mensagem")
    private String erroMensagem;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "lancamento_gerado_id")
    private UUID lancamentoGeradoId;

    @Column(name = "compra_gerada_id")
    private UUID compraGeradaId;

    @Column(name = "iniciado_em")
    private Instant iniciadoEm;

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    protected ProcessamentoDocumento() {
        // exigido pelo JPA
    }

    /** Estado inicial do pipeline (PENDENTE) - criado junto com o Documento no upload (Fluxo 6.3). */
    public static ProcessamentoDocumento novo(UUID ambienteFinanceiroId, UUID documentoId) {
        if (ambienteFinanceiroId == null) {
            throw new RegraDeNegocioException("AMBIENTE_FINANCEIRO_OBRIGATORIO", "ambienteFinanceiroId e obrigatorio.");
        }
        if (documentoId == null) {
            throw new RegraDeNegocioException("DOCUMENTO_OBRIGATORIO", "documentoId e obrigatorio.");
        }
        ProcessamentoDocumento processamento = new ProcessamentoDocumento();
        processamento.ambienteFinanceiroId = ambienteFinanceiroId;
        processamento.documentoId = documentoId;
        processamento.status = StatusProcessamento.PENDENTE;
        processamento.tentativas = 0;
        return processamento;
    }

    /**
     * PENDENTE|ERRO -> PROCESSANDO. Conta a tentativa, marca o inicio e limpa o
     * resultado da tentativa anterior (retry a partir de ERRO - Sec6.5).
     */
    public void iniciarProcessamento() {
        if (status != StatusProcessamento.PENDENTE && status != StatusProcessamento.ERRO) {
            throw new RegraDeNegocioException("TRANSICAO_INVALIDA",
                    "So e possivel iniciar o processamento de um documento PENDENTE ou em ERRO.");
        }
        this.status = StatusProcessamento.PROCESSANDO;
        this.tentativas = this.tentativas + 1;
        this.iniciadoEm = Instant.now();
        this.concluidoEm = null;
        this.erroMensagem = null;
    }

    /**
     * PROCESSANDO -> AGUARDANDO_REVISAO. A cascata extraiu os dados; o usuario
     * ainda precisa revisar e confirmar antes de gerar Lancamento/Compra (RN-08).
     */
    public void registrarExtracao(String dadosExtraidos) {
        if (status != StatusProcessamento.PROCESSANDO) {
            throw new RegraDeNegocioException("TRANSICAO_INVALIDA",
                    "So e possivel registrar extracao de um documento em PROCESSANDO.");
        }
        if (dadosExtraidos == null || dadosExtraidos.isBlank()) {
            throw new RegraDeNegocioException("DADOS_EXTRAIDOS_OBRIGATORIOS",
                    "A extracao concluida precisa de dadosExtraidos.");
        }
        this.dadosExtraidos = dadosExtraidos;
        this.status = StatusProcessamento.AGUARDANDO_REVISAO;
        this.concluidoEm = Instant.now();
    }

    /**
     * PROCESSANDO -> ERRO. Falha da cascata (todas as etapas, incluindo o
     * fallback OpenAI quando habilitado). Nunca gera Lancamento com dado
     * incerto (RN-08); retry manual disponivel via {@link #iniciarProcessamento()}.
     */
    public void registrarErro(String mensagem) {
        if (status != StatusProcessamento.PROCESSANDO) {
            throw new RegraDeNegocioException("TRANSICAO_INVALIDA",
                    "So e possivel registrar erro de um documento em PROCESSANDO.");
        }
        this.status = StatusProcessamento.ERRO;
        this.erroMensagem = mensagem;
        this.concluidoEm = Instant.now();
    }

    /**
     * AGUARDANDO_REVISAO -> CONFIRMADO. O usuario revisou os dados e confirmou;
     * exatamente um de {@code lancamentoGeradoId}/{@code compraGeradaId} e
     * preenchido (o documento gera um Lancamento ou uma Compra, nunca os dois
     * nem nenhum) - SAD Sec4.15, Sec6.5.
     */
    public void confirmar(UUID lancamentoGeradoId, UUID compraGeradaId) {
        if (status != StatusProcessamento.AGUARDANDO_REVISAO) {
            throw new RegraDeNegocioException("TRANSICAO_INVALIDA",
                    "So e possivel confirmar um documento em AGUARDANDO_REVISAO.");
        }
        boolean gerouLancamento = lancamentoGeradoId != null;
        boolean gerouCompra = compraGeradaId != null;
        if (gerouLancamento == gerouCompra) {
            throw new RegraDeNegocioException("RESULTADO_CONFIRMACAO_INVALIDO",
                    "A confirmacao gera exatamente um: Lancamento ou Compra.");
        }
        this.lancamentoGeradoId = lancamentoGeradoId;
        this.compraGeradaId = compraGeradaId;
        this.status = StatusProcessamento.CONFIRMADO;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAmbienteFinanceiroId() {
        return ambienteFinanceiroId;
    }

    public UUID getDocumentoId() {
        return documentoId;
    }

    public StatusProcessamento getStatus() {
        return status;
    }

    public String getDadosExtraidos() {
        return dadosExtraidos;
    }

    public String getErroMensagem() {
        return erroMensagem;
    }

    public int getTentativas() {
        return tentativas;
    }

    public UUID getLancamentoGeradoId() {
        return lancamentoGeradoId;
    }

    public UUID getCompraGeradaId() {
        return compraGeradaId;
    }

    public Instant getIniciadoEm() {
        return iniciadoEm;
    }

    public Instant getConcluidoEm() {
        return concluidoEm;
    }
}
