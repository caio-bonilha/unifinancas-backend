package br.com.unifinancas.presentation.rest.documento;

import br.com.unifinancas.application.documento.DocumentoResumo;
import br.com.unifinancas.application.documento.DocumentoService;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.DocumentoResponse;
import br.com.unifinancas.presentation.dto.VincularDocumentoRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * `[Revisado - Etapa B]` Vinculo Documento &lt;-&gt; Lancamento (N:1): um
 * Lancamento pode ter varios Documentos, cada Documento pertence a no maximo
 * um. Dois endpoints, em prefixos de rota diferentes, por isso um controller
 * proprio:
 *
 * <ul>
 *   <li>{@code GET /v1/lancamentos/{lancamentoId}/documentos} — arquivos
 *       vinculados a um Lancamento.</li>
 *   <li>{@code PATCH /v1/documentos/{documentoId}} {@code {lancamentoId}} —
 *       vincula (aditivo) ou desvincula ({@code lancamentoId} nulo). Nunca
 *       apaga o Documento (SAD: sem exclusao permanente de dados).</li>
 * </ul>
 */
@RestController
public class DocumentoVinculoController {

    private final DocumentoService documentoService;

    public DocumentoVinculoController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @GetMapping("/v1/lancamentos/{lancamentoId}/documentos")
    public List<DocumentoResponse> listarDoLancamento(TenantContext contexto, @PathVariable UUID lancamentoId) {
        return documentoService.listarDoLancamento(contexto, lancamentoId).stream()
                .map(DocumentoVinculoController::paraResponse)
                .toList();
    }

    @PatchMapping("/v1/documentos/{documentoId}")
    public DocumentoResponse vincular(TenantContext contexto, @PathVariable UUID documentoId,
            @RequestBody VincularDocumentoRequest request) {
        DocumentoResumo resumo = documentoService.vincularLancamento(contexto, documentoId,
                request == null ? null : request.lancamentoId());
        return paraResponse(resumo);
    }

    private static DocumentoResponse paraResponse(DocumentoResumo r) {
        return new DocumentoResponse(r.id(), r.tipo(), r.nomeArquivo(), r.tamanhoBytes(), r.mimeType(),
                r.urlAssinada(), r.criadoEm(), r.lancamentoId());
    }
}
