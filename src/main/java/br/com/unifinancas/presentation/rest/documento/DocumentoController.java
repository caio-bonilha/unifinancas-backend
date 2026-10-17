package br.com.unifinancas.presentation.rest.documento;

import br.com.unifinancas.application.documento.ConfirmacaoDocumentoResultado;
import br.com.unifinancas.application.documento.DadosRevisadosLancamento;
import br.com.unifinancas.application.documento.DocumentoResumo;
import br.com.unifinancas.application.documento.DocumentoService;
import br.com.unifinancas.application.documento.ProcessamentoDocumentoDetalhe;
import br.com.unifinancas.application.documento.ProcessamentoDocumentoResumo;
import br.com.unifinancas.application.documento.ProcessamentoDocumentoService;
import br.com.unifinancas.application.lancamento.LancamentoResumo;
import br.com.unifinancas.domain.documento.StatusProcessamento;
import br.com.unifinancas.domain.documento.TipoDocumento;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.ConfirmarProcessamentoRequest;
import br.com.unifinancas.presentation.dto.ConfirmarProcessamentoResponse;
import br.com.unifinancas.presentation.dto.DocumentoParaProcessamentoResponse;
import br.com.unifinancas.presentation.dto.DocumentoResponse;
import br.com.unifinancas.presentation.dto.LancamentoResponse;
import br.com.unifinancas.presentation.dto.ProcessamentoDocumentoResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * SAD §7.2, RF-16/RF-20. Duas famílias de fluxo sob {@code /v1/documentos}:
 *
 * <ul>
 *   <li><b>com {@code lancamentoId}</b> (T1.8.3): anexa o arquivo a um
 *       Lançamento existente, sem processamento.</li>
 *   <li><b>sem {@code lancamentoId}</b> (T3.1.5, Fluxo 6.3): upload para
 *       processamento assistido — cria {@code ProcessamentoDocumento} e
 *       enfileira a extração; depois {@code GET /{id}} para revisar e
 *       {@code PATCH /{id}/confirmar} para gerar o Lançamento (RN-08). O
 *       {@code {id}} nessas rotas é o id do <b>Documento</b> (1:1 com o
 *       ProcessamentoDocumento).</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/documentos")
public class DocumentoController {

    private final DocumentoService documentoService;
    private final ProcessamentoDocumentoService processamentoDocumentoService;
    private final ObjectMapper objectMapper;

    public DocumentoController(DocumentoService documentoService,
            ProcessamentoDocumentoService processamentoDocumentoService, ObjectMapper objectMapper) {
        this.documentoService = documentoService;
        this.processamentoDocumentoService = processamentoDocumentoService;
        this.objectMapper = objectMapper;
    }

    /** T1.8.3, RF-16: upload + vínculo a um Lançamento existente. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, params = "lancamentoId")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoResponse anexar(TenantContext contexto, @RequestParam UUID lancamentoId,
            @RequestParam TipoDocumento tipo, @RequestParam MultipartFile arquivo) throws IOException {
        DocumentoResumo resumo = documentoService.anexarAoLancamento(contexto, lancamentoId, tipo,
                arquivo.getBytes(), arquivo.getOriginalFilename(), arquivo.getContentType());
        return new DocumentoResponse(resumo.id(), resumo.tipo(), resumo.nomeArquivo(), resumo.tamanhoBytes(),
                resumo.mimeType(), resumo.urlAssinada(), resumo.criadoEm(), resumo.lancamentoId());
    }

    /** T3.1.5, Fluxo 6.3: upload para processamento assistido (sem Lançamento ainda). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, params = "!lancamentoId")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentoParaProcessamentoResponse enviarParaProcessamento(TenantContext contexto,
            @RequestParam TipoDocumento tipo, @RequestParam MultipartFile arquivo) throws IOException {
        ProcessamentoDocumentoResumo resumo = processamentoDocumentoService.registrarParaProcessamento(contexto, tipo,
                arquivo.getBytes(), arquivo.getOriginalFilename(), arquivo.getContentType());
        return new DocumentoParaProcessamentoResponse(resumo.documentoId(), resumo.id(), resumo.tipo(),
                resumo.nomeArquivo(), resumo.status());
    }

    /** SAD §7.2 (T3.1.7): central de revisão — lista os processamentos do Ambiente Financeiro, opcionalmente por status. */
    @GetMapping
    public List<ProcessamentoDocumentoResponse> listarProcessamentos(TenantContext contexto,
            @RequestParam(required = false) List<StatusProcessamento> status) {
        return processamentoDocumentoService.listar(contexto, status).stream()
                .map(this::paraResponse)
                .toList();
    }

    /** SAD §7.2: status do processamento + dados extraídos para revisão. */
    @GetMapping("/{id}")
    public ProcessamentoDocumentoResponse consultarProcessamento(TenantContext contexto, @PathVariable UUID id) {
        return paraResponse(processamentoDocumentoService.consultarPorDocumento(contexto, id));
    }

    /**
     * ADR-020 (T3.1.12): soft-delete do Documento — some das listagens no ato;
     * a purga assíncrona apaga em definitivo depois da retenção. Bloqueado
     * enquanto o processamento estiver em andamento (422 {@code DOCUMENTO_EM_PROCESSAMENTO}).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(TenantContext contexto, @PathVariable UUID id) {
        documentoService.excluir(contexto, id);
    }

    /** SAD §7.2, Fluxo 6.3 (RN-08): confirma os dados revisados e gera o Lançamento. */
    @PatchMapping("/{id}/confirmar")
    public ConfirmarProcessamentoResponse confirmar(TenantContext contexto, @PathVariable UUID id,
            @Valid @RequestBody ConfirmarProcessamentoRequest request) {
        DadosRevisadosLancamento dados = new DadosRevisadosLancamento(request.tipo(), request.valor(),
                request.dataEmissao(), request.dataVencimento(), request.categoriaId(), request.contatoId(),
                request.contaBancariaId(), request.formaPagamento(), request.descricao(), request.observacao(),
                request.linhaDigitavel());
        ConfirmacaoDocumentoResultado resultado = processamentoDocumentoService.confirmarPorDocumento(contexto, id, dados);
        return new ConfirmarProcessamentoResponse(resultado.processamentoDocumentoId(), resultado.status(),
                paraResponse(resultado.lancamento()));
    }

    private JsonNode paraJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            // JSON gravado pela cascata (T3.1.3) - nao deve ser invalido.
            throw new IllegalStateException("dados_extraidos armazenado nao e um JSON valido.", e);
        }
    }

    private ProcessamentoDocumentoResponse paraResponse(ProcessamentoDocumentoDetalhe d) {
        return new ProcessamentoDocumentoResponse(d.id(), d.documentoId(), d.tipo(), d.nomeArquivo(), d.status(),
                paraJson(d.dadosExtraidosJson()), d.erroMensagem(), d.tentativas(), d.lancamentoGeradoId());
    }

    private LancamentoResponse paraResponse(LancamentoResumo r) {
        return new LancamentoResponse(r.id(), r.tipo(), r.status(), r.valor(), r.dataEmissao(), r.dataVencimento(),
                r.dataPagamento(), r.categoriaId(), r.contatoId(), r.contaBancariaId(), r.formaPagamento(), r.origem(),
                r.descricao(), r.observacao(), r.linhaDigitavel());
    }
}
