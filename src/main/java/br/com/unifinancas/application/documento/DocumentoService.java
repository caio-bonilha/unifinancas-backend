package br.com.unifinancas.application.documento;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.documento.Documento;
import br.com.unifinancas.domain.documento.DocumentoRepository;
import br.com.unifinancas.domain.documento.ProcessamentoDocumento;
import br.com.unifinancas.domain.documento.ProcessamentoDocumentoRepository;
import br.com.unifinancas.domain.documento.StatusProcessamento;
import br.com.unifinancas.domain.documento.TipoDocumento;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-16, SAD Sec7.2. Upload e vinculo de arquivos a Lancamentos — sem
 * {@code ProcessamentoDocumento} (processamento assistido e a Fase 3, ver
 * {@code ProcessamentoDocumentoService}). Cada Ambiente Financeiro tem seu
 * proprio "diretorio" no bucket (chave prefixada por
 * {@code ambienteFinanceiroId}), reforcando o isolamento entre tenants
 * tambem no Object Storage (Sec8.7/Sec12.4).
 *
 * <p>`[Revisado - Etapa B]` O vinculo Documento <-> Lancamento e N:1: um
 * Lancamento pode ter varios Documentos (boleto + comprovante + nota...), e
 * o lado "dono" da FK mora em {@code documento.lancamento_id}. Vincular e
 * desvincular so mexem nessa referencia.
 *
 * <p>`[ADR-020]` Excluir e diferente de desvincular: {@link #excluir} faz um
 * soft-delete (o Documento some das listagens na hora, reversivel ate a
 * purga assincrona levar embora em definitivo apos a janela de retencao).
 */
@Service
public class DocumentoService {

    private final DocumentoRepository documentoRepository;
    private final LancamentoRepository lancamentoRepository;
    private final ProcessamentoDocumentoRepository processamentoDocumentoRepository;
    private final DocumentoStoragePort documentoStoragePort;

    public DocumentoService(DocumentoRepository documentoRepository, LancamentoRepository lancamentoRepository,
            ProcessamentoDocumentoRepository processamentoDocumentoRepository,
            DocumentoStoragePort documentoStoragePort) {
        this.documentoRepository = documentoRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.processamentoDocumentoRepository = processamentoDocumentoRepository;
        this.documentoStoragePort = documentoStoragePort;
    }

    /** SAD Sec8.2: escrita financeira (anexar documento altera o Lancamento) exige OWNER, ADMIN ou MEMBRO. */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public DocumentoResumo anexarAoLancamento(TenantContext contexto, UUID lancamentoId, TipoDocumento tipo,
            byte[] conteudo, String nomeArquivoOriginal, String mimeType) {
        exigirLancamentoDoAmbiente(contexto, lancamentoId);

        String chave = gerarChave(contexto.ambienteFinanceiroId(), nomeArquivoOriginal);
        documentoStoragePort.armazenar(chave, conteudo, mimeType);

        Documento documento = Documento.novo(contexto.ambienteFinanceiroId(), tipo, chave, nomeArquivoOriginal,
                conteudo.length, mimeType, contexto.usuarioId());
        documento.vincularALancamento(lancamentoId);
        documentoRepository.save(documento);

        return paraResumo(documento);
    }

    /**
     * `[Revisado - Etapa B]` PATCH /v1/documentos/{id}: vincula o Documento a
     * um Lancamento ({@code lancamentoId} != null — vinculo aditivo, N:1) ou
     * desfaz o vinculo ({@code lancamentoId} == null). Nao exclui o Documento.
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public DocumentoResumo vincularLancamento(TenantContext contexto, UUID documentoId, UUID lancamentoId) {
        Documento documento = exigirDocumentoDoAmbiente(contexto, documentoId);

        if (lancamentoId == null) {
            documento.desvincularDoLancamento();
        } else {
            exigirLancamentoDoAmbiente(contexto, lancamentoId);
            documento.vincularALancamento(lancamentoId);
        }
        documentoRepository.save(documento);
        return paraResumo(documento);
    }

    /**
     * `[ADR-020]` DELETE /v1/documentos/{id}: soft-delete. O Documento some das
     * listagens no ato (arquivos do Lancamento, central de revisao) e a
     * operacao e reversivel ate a purga assincrona ({@code PurgaDocumentoJob})
     * apagar em definitivo apos a janela de retencao. Bloqueada enquanto houver
     * um {@link ProcessamentoDocumento} em andamento (PENDENTE/PROCESSANDO) — o
     * worker ainda esta mexendo no arquivo.
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public void excluir(TenantContext contexto, UUID documentoId) {
        Documento documento = exigirDocumentoDoAmbiente(contexto, documentoId);

        processamentoDocumentoRepository
                .findByDocumentoIdAndAmbienteFinanceiroId(documentoId, contexto.ambienteFinanceiroId())
                .filter(DocumentoService::estaEmAndamento)
                .ifPresent(p -> {
                    throw new RegraDeNegocioException("DOCUMENTO_EM_PROCESSAMENTO",
                            "Nao e possivel excluir um documento enquanto o processamento esta em andamento (atual: "
                                    + p.getStatus() + ").");
                });

        documento.excluir(contexto.usuarioId());
        documentoRepository.save(documento);
    }

    /** `[Revisado - Etapa B]` GET /v1/lancamentos/{id}/documentos: arquivos vinculados a um Lancamento (N:1). */
    @Transactional(readOnly = true)
    public List<DocumentoResumo> listarDoLancamento(TenantContext contexto, UUID lancamentoId) {
        exigirLancamentoDoAmbiente(contexto, lancamentoId);
        return documentoRepository
                .findAllByLancamentoIdAndAmbienteFinanceiroIdAndExcluidoEmIsNullOrderByCriadoEmDesc(
                        lancamentoId, contexto.ambienteFinanceiroId())
                .stream()
                .map(this::paraResumo)
                .toList();
    }

    private static boolean estaEmAndamento(ProcessamentoDocumento processamento) {
        return processamento.getStatus() == StatusProcessamento.PENDENTE
                || processamento.getStatus() == StatusProcessamento.PROCESSANDO;
    }

    private Documento exigirDocumentoDoAmbiente(TenantContext contexto, UUID documentoId) {
        return documentoRepository
                .findByIdAndAmbienteFinanceiroIdAndExcluidoEmIsNull(documentoId, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "DOCUMENTO_NAO_ENCONTRADO", "Documento nao encontrado neste Ambiente Financeiro."));
    }

    private void exigirLancamentoDoAmbiente(TenantContext contexto, UUID lancamentoId) {
        lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "LANCAMENTO_NAO_ENCONTRADO", "Lancamento nao encontrado neste Ambiente Financeiro."));
    }

    /** Prefixo por ambiente reforca isolamento entre tenants tambem no bucket; sufixo aleatorio evita colisao entre uploads com o mesmo nome de arquivo. */
    private String gerarChave(UUID ambienteFinanceiroId, String nomeArquivoOriginal) {
        return ambienteFinanceiroId + "/" + UUID.randomUUID() + "-" + nomeArquivoOriginal;
    }

    private DocumentoResumo paraResumo(Documento documento) {
        String urlAssinada = documentoStoragePort.gerarUrlAssinada(documento.getArquivoUrl());
        return new DocumentoResumo(documento.getId(), documento.getTipo(), documento.getNomeArquivo(),
                documento.getTamanhoBytes(), documento.getMimeType(), urlAssinada, documento.getCriadoEm(),
                documento.getLancamentoId());
    }
}
