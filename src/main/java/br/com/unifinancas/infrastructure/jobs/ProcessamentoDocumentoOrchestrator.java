package br.com.unifinancas.infrastructure.jobs;

import br.com.unifinancas.application.documento.DocumentoStoragePort;
import br.com.unifinancas.application.documento.EntradaExtracao;
import br.com.unifinancas.application.documento.ProcessamentoDocumentoPort;
import br.com.unifinancas.application.documento.ResultadoExtracao;
import br.com.unifinancas.application.notificacao.NotificacaoService;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiroRepository;
import br.com.unifinancas.domain.documento.Documento;
import br.com.unifinancas.domain.documento.DocumentoRepository;
import br.com.unifinancas.domain.documento.ProcessamentoDocumento;
import br.com.unifinancas.domain.documento.ProcessamentoDocumentoRepository;
import br.com.unifinancas.domain.documento.StatusProcessamento;
import br.com.unifinancas.domain.notificacao.CanalNotificacao;
import br.com.unifinancas.domain.notificacao.TipoNotificacao;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * T3.1.4, SAD Fluxo 6.3: worker assíncrono do pipeline de extração de
 * documento. Consome o job {@code PROCESSAMENTO_DOCUMENTO} (enfileirado por
 * {@link EnfileiradorProcessamentoDocumentoJpaAdapter}), baixa o arquivo do
 * Object Storage e chama a cascata ({@link ProcessamentoDocumentoPort},
 * T3.1.3). Move o {@link ProcessamentoDocumento} para
 * {@code AGUARDANDO_REVISAO} (sucesso) ou {@code ERRO} (falha) — <b>nunca</b>
 * cria Lançamento (RN-08; isso é a confirmação do usuário, T3.1.5).
 *
 * <p>Idempotente (RNF-02): reexecutar o job para um documento já em
 * {@code AGUARDANDO_REVISAO}/{@code CONFIRMADO} é no-op. Uma falha de extração
 * <b>não</b> derruba o job (fica {@code CONCLUIDO}) — o erro fica registrado
 * no próprio {@code ProcessamentoDocumento} e o retry é manual (Fluxo 6.3,
 * passo 7). O job só falha (e é retentado pelo {@code JobWorker}) se nem o
 * registro puder ser lido.
 *
 * <p>`[T3.1.6]` Ao final, notifica <b>quem fez o upload</b> ({@code
 * documento.enviadoPorUsuarioId}): {@code DOCUMENTO_AGUARDANDO_REVISAO} no
 * sucesso, {@code FALHA_PROCESSAMENTO} no erro (Fluxo 6.3, passos 6-7) — IN_APP
 * sempre, mais PUSH/EMAIL. A idempotência acima evita renotificar num re-run.
 */
@Component
public class ProcessamentoDocumentoOrchestrator implements JobHandler {

    public static final String TIPO = "PROCESSAMENTO_DOCUMENTO";

    private static final String REFERENCIA_TIPO = "processamento_documento";
    private static final Set<CanalNotificacao> CANAIS = Set.of(CanalNotificacao.PUSH, CanalNotificacao.EMAIL);

    private static final Logger log = LoggerFactory.getLogger(ProcessamentoDocumentoOrchestrator.class);

    private final ProcessamentoDocumentoRepository processamentoDocumentoRepository;
    private final DocumentoRepository documentoRepository;
    private final AmbienteFinanceiroRepository ambienteFinanceiroRepository;
    private final DocumentoStoragePort documentoStoragePort;
    private final ProcessamentoDocumentoPort processamentoDocumentoPort;
    private final NotificacaoService notificacaoService;
    private final ObjectMapper objectMapper;

    public ProcessamentoDocumentoOrchestrator(ProcessamentoDocumentoRepository processamentoDocumentoRepository,
            DocumentoRepository documentoRepository, AmbienteFinanceiroRepository ambienteFinanceiroRepository,
            DocumentoStoragePort documentoStoragePort, ProcessamentoDocumentoPort processamentoDocumentoPort,
            NotificacaoService notificacaoService, ObjectMapper objectMapper) {
        this.processamentoDocumentoRepository = processamentoDocumentoRepository;
        this.documentoRepository = documentoRepository;
        this.ambienteFinanceiroRepository = ambienteFinanceiroRepository;
        this.documentoStoragePort = documentoStoragePort;
        this.processamentoDocumentoPort = processamentoDocumentoPort;
        this.notificacaoService = notificacaoService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public void executar(JobExecucao job) {
        UUID processamentoId = extrairProcessamentoId(job.getPayload());
        ProcessamentoDocumento processamento = processamentoDocumentoRepository.findById(processamentoId)
                .orElseThrow(() -> new IllegalStateException(
                        "ProcessamentoDocumento " + processamentoId + " nao encontrado."));

        if (processamento.getStatus() != StatusProcessamento.PENDENTE
                && processamento.getStatus() != StatusProcessamento.ERRO) {
            log.info("ProcessamentoDocumento {} ja esta em {} - job ignorado (idempotencia).",
                    processamentoId, processamento.getStatus());
            return;
        }

        processamento.iniciarProcessamento();
        Documento documento = null;
        try {
            documento = documentoRepository.findById(processamento.getDocumentoId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Documento " + processamento.getDocumentoId() + " nao encontrado."));
            ResultadoExtracao resultado = extrair(processamento, documento);
            if (resultado.sucesso()) {
                processamento.registrarExtracao(resultado.dadosExtraidosJson());
                log.info("ProcessamentoDocumento {} extraido pela cascata ({}) - AGUARDANDO_REVISAO.",
                        processamentoId, resultado.origem());
            } else {
                processamento.registrarErro(resultado.motivo());
                log.info("ProcessamentoDocumento {} sem extracao: {}", processamentoId, resultado.motivo());
            }
        } catch (RuntimeException e) {
            processamento.registrarErro("Falha ao processar o documento: " + e.getMessage());
            log.error("Falha inesperada no ProcessamentoDocumento {}", processamentoId, e);
        }
        processamentoDocumentoRepository.save(processamento);

        notificarResultado(processamento, documento);
    }

    private ResultadoExtracao extrair(ProcessamentoDocumento processamento, Documento documento) {
        byte[] conteudo = documentoStoragePort.baixar(documento.getArquivoUrl());
        boolean fallbackIa = ambienteFinanceiroRepository.findById(processamento.getAmbienteFinanceiroId())
                .map(AmbienteFinanceiro::isOcrFallbackIaHabilitado)
                .orElse(false);

        EntradaExtracao entrada = new EntradaExtracao(conteudo, documento.getMimeType(), documento.getNomeArquivo(),
                documento.getTipo(), fallbackIa);
        return processamentoDocumentoPort.extrair(entrada);
    }

    /** T3.1.6, Fluxo 6.3 passos 6-7 — notifica quem fez o upload conforme o status final. */
    private void notificarResultado(ProcessamentoDocumento processamento, Documento documento) {
        if (documento == null) {
            log.warn("Sem Documento carregado - resultado do ProcessamentoDocumento {} nao sera notificado.",
                    processamento.getId());
            return;
        }
        UUID destinatario = documento.getEnviadoPorUsuarioId();
        String arquivo = documento.getNomeArquivo();
        // referenciaId = id do Documento (chave da API de revisao GET/PATCH /v1/documentos/{id}),
        // para o indicador de notificacoes conseguir abrir a tela de revisao direto (T3.1.7).
        UUID referenciaId = documento.getId();

        if (processamento.getStatus() == StatusProcessamento.AGUARDANDO_REVISAO) {
            notificacaoService.criar(processamento.getAmbienteFinanceiroId(), destinatario,
                    TipoNotificacao.DOCUMENTO_AGUARDANDO_REVISAO,
                    "Documento pronto para revisao",
                    "O documento \"" + arquivo + "\" foi processado e esta aguardando sua revisao.",
                    REFERENCIA_TIPO, referenciaId, CANAIS);
        } else if (processamento.getStatus() == StatusProcessamento.ERRO) {
            notificacaoService.criar(processamento.getAmbienteFinanceiroId(), destinatario,
                    TipoNotificacao.FALHA_PROCESSAMENTO,
                    "Falha ao processar documento",
                    "Nao foi possivel extrair os dados de \"" + arquivo + "\": " + processamento.getErroMensagem()
                            + " Voce pode tentar novamente.",
                    REFERENCIA_TIPO, referenciaId, CANAIS);
        }
    }

    private UUID extrairProcessamentoId(String payloadJson) {
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            return UUID.fromString(node.get("processamentoDocumentoId").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Payload invalido para job " + TIPO + ": " + payloadJson, e);
        }
    }
}
