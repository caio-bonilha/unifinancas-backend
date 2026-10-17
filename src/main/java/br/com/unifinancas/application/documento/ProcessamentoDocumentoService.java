package br.com.unifinancas.application.documento;

import br.com.unifinancas.application.lancamento.LancamentoResumo;
import br.com.unifinancas.application.lancamento.LancamentoService;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.documento.Documento;
import br.com.unifinancas.domain.documento.DocumentoRepository;
import br.com.unifinancas.domain.documento.ProcessamentoDocumento;
import br.com.unifinancas.domain.documento.ProcessamentoDocumentoRepository;
import br.com.unifinancas.domain.documento.StatusProcessamento;
import br.com.unifinancas.domain.documento.TipoDocumento;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processamento assistido de documento (RF-20, Fluxo 6.3) — lados de entrada
 * e de confirmação:
 *
 * <ul>
 *   <li>{@link #registrarParaProcessamento}: upload + {@link Documento}
 *       avulso + {@link ProcessamentoDocumento} {@code PENDENTE} + enfileira
 *       o job (T3.1.4).</li>
 *   <li>{@link #consultarPorDocumento}: estado + dados extraídos para a tela
 *       de revisão (T3.1.5, {@code GET /v1/documentos/{id}}).</li>
 *   <li>{@link #confirmarPorDocumento}: com os dados revisados pelo usuário,
 *       cria o Lançamento (origem DOCUMENTO) e marca o processamento
 *       {@code CONFIRMADO} — <b>nunca</b> automático (RN-08).</li>
 * </ul>
 *
 * <p>Diferente de {@code DocumentoService.anexarAoLancamento} (T1.8.3), aqui
 * o {@link Documento} nasce <b>avulso</b> — o Lançamento só passa a existir
 * na confirmação.
 */
@Service
public class ProcessamentoDocumentoService {

    private final DocumentoRepository documentoRepository;
    private final ProcessamentoDocumentoRepository processamentoDocumentoRepository;
    private final DocumentoStoragePort documentoStoragePort;
    private final EnfileiradorProcessamentoDocumentoPort enfileirador;
    private final LancamentoService lancamentoService;

    public ProcessamentoDocumentoService(DocumentoRepository documentoRepository,
            ProcessamentoDocumentoRepository processamentoDocumentoRepository,
            DocumentoStoragePort documentoStoragePort,
            EnfileiradorProcessamentoDocumentoPort enfileirador,
            LancamentoService lancamentoService) {
        this.documentoRepository = documentoRepository;
        this.processamentoDocumentoRepository = processamentoDocumentoRepository;
        this.documentoStoragePort = documentoStoragePort;
        this.enfileirador = enfileirador;
        this.lancamentoService = lancamentoService;
    }

    /** SAD Sec8.2: cria dado que vira Lançamento — exige OWNER, ADMIN ou MEMBRO. */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public ProcessamentoDocumentoResumo registrarParaProcessamento(TenantContext contexto, TipoDocumento tipo,
            byte[] conteudo, String nomeArquivoOriginal, String mimeType) {
        String chave = gerarChave(contexto.ambienteFinanceiroId(), nomeArquivoOriginal);
        documentoStoragePort.armazenar(chave, conteudo, mimeType);

        Documento documento = Documento.novo(contexto.ambienteFinanceiroId(), tipo, chave, nomeArquivoOriginal,
                conteudo.length, mimeType, contexto.usuarioId());
        documentoRepository.save(documento);

        ProcessamentoDocumento processamento = ProcessamentoDocumento.novo(
                contexto.ambienteFinanceiroId(), documento.getId());
        processamentoDocumentoRepository.save(processamento);

        enfileirador.enfileirar(processamento.getId());

        return new ProcessamentoDocumentoResumo(processamento.getId(), documento.getId(), documento.getTipo(),
                documento.getNomeArquivo(), processamento.getStatus());
    }

    /** GET /v1/documentos/{id}: {@code id} e o id do Documento (1:1 com o ProcessamentoDocumento). */
    @Transactional(readOnly = true)
    public ProcessamentoDocumentoDetalhe consultarPorDocumento(TenantContext contexto, UUID documentoId) {
        ProcessamentoDocumento processamento = localizar(contexto, documentoId);
        Documento documento = carregarDocumento(processamento);
        return paraDetalhe(processamento, documento);
    }

    /**
     * GET /v1/documentos (T3.1.7): central de revisão do Ambiente Financeiro,
     * opcionalmente filtrada por status (mais recente primeiro). Sem
     * {@code @RequerPerfil} — leitura vale para qualquer perfil ativo.
     */
    @Transactional(readOnly = true)
    public List<ProcessamentoDocumentoDetalhe> listar(TenantContext contexto, Collection<StatusProcessamento> status) {
        List<ProcessamentoDocumento> processamentos = (status == null || status.isEmpty())
                ? processamentoDocumentoRepository.findAllByAmbienteFinanceiroIdOrderByIdDesc(contexto.ambienteFinanceiroId())
                : processamentoDocumentoRepository.findAllByAmbienteFinanceiroIdAndStatusInOrderByIdDesc(
                        contexto.ambienteFinanceiroId(), status);

        Map<UUID, Documento> documentosPorId = documentoRepository
                .findAllById(processamentos.stream().map(ProcessamentoDocumento::getDocumentoId).toList()).stream()
                .collect(Collectors.toMap(Documento::getId, d -> d));

        // `[ADR-020]` um Documento soft-deletado nao existe mais para o negocio;
        // o processamento dele nao aparece na central de revisao.
        return processamentos.stream()
                .filter(p -> {
                    Documento documento = documentosPorId.get(p.getDocumentoId());
                    return documento != null && !documento.isExcluido();
                })
                .map(p -> paraDetalhe(p, documentosPorId.get(p.getDocumentoId())))
                .toList();
    }

    private ProcessamentoDocumentoDetalhe paraDetalhe(ProcessamentoDocumento processamento, Documento documento) {
        if (documento == null) {
            throw new RecursoNaoEncontradoException("DOCUMENTO_NAO_ENCONTRADO", "Documento de origem nao encontrado.");
        }
        return new ProcessamentoDocumentoDetalhe(processamento.getId(), documento.getId(), documento.getTipo(),
                documento.getNomeArquivo(), processamento.getStatus(), processamento.getDadosExtraidos(),
                processamento.getErroMensagem(), processamento.getTentativas(), processamento.getLancamentoGeradoId());
    }

    /**
     * PATCH /v1/documentos/{id}/confirmar: com os dados revisados pelo usuário,
     * cria o Lançamento (origem DOCUMENTO, via {@link LancamentoService}) e
     * move o processamento para {@code CONFIRMADO}. Só a partir de
     * {@code AGUARDANDO_REVISAO} (RN-08).
     *
     * <p>TODO T2.2.2: quando existir {@code CompraVendaService}, este método
     * (ou um irmão) passará a poder gerar uma {@code Compra} em vez de um
     * Lançamento — {@link ProcessamentoDocumento#confirmar} já aceita os dois.
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public ConfirmacaoDocumentoResultado confirmarPorDocumento(TenantContext contexto, UUID documentoId,
            DadosRevisadosLancamento dados) {
        ProcessamentoDocumento processamento = localizar(contexto, documentoId);
        if (processamento.getStatus() != StatusProcessamento.AGUARDANDO_REVISAO) {
            throw new RegraDeNegocioException("PROCESSAMENTO_NAO_AGUARDA_REVISAO",
                    "So e possivel confirmar um documento em AGUARDANDO_REVISAO (atual: " + processamento.getStatus() + ").");
        }

        LancamentoResumo lancamento = lancamentoService.criarDeDocumento(contexto, dados.tipo(), dados.valor(),
                dados.dataEmissao(), dados.dataVencimento(), dados.categoriaId(), dados.contatoId(),
                dados.contaBancariaId(), dados.formaPagamento(),
                dados.descricao(), dados.observacao(), dados.linhaDigitavel());

        // `[Revisado - Etapa B]` o vinculo Documento -> Lancamento (N:1) mora
        // agora em documento.lancamento_id; o proprio documento processado
        // fica vinculado ao Lancamento recem-criado.
        Documento documento = carregarDocumento(processamento);
        documento.vincularALancamento(lancamento.id());
        documentoRepository.save(documento);

        processamento.confirmar(lancamento.id(), null);
        processamentoDocumentoRepository.save(processamento);

        return new ConfirmacaoDocumentoResultado(processamento.getId(), processamento.getStatus(), lancamento);
    }

    private ProcessamentoDocumento localizar(TenantContext contexto, UUID documentoId) {
        return processamentoDocumentoRepository
                .findByDocumentoIdAndAmbienteFinanceiroId(documentoId, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("PROCESSAMENTO_DOCUMENTO_NAO_ENCONTRADO",
                        "Nao ha processamento para este documento neste Ambiente Financeiro."));
    }

    private Documento carregarDocumento(ProcessamentoDocumento processamento) {
        // `[ADR-020]` Documento soft-deletado conta como inexistente (a revisao/
        // confirmacao de um documento excluido devolve 404, nao um estado zumbi).
        return documentoRepository.findById(processamento.getDocumentoId())
                .filter(documento -> !documento.isExcluido())
                .orElseThrow(() -> new RecursoNaoEncontradoException("DOCUMENTO_NAO_ENCONTRADO",
                        "Documento de origem nao encontrado."));
    }

    /** Prefixo por ambiente reforca o isolamento no bucket; sufixo aleatorio evita colisao de nomes (igual DocumentoService). */
    private String gerarChave(UUID ambienteFinanceiroId, String nomeArquivoOriginal) {
        return ambienteFinanceiroId + "/" + UUID.randomUUID() + "-" + nomeArquivoOriginal;
    }
}
