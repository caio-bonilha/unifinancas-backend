package br.com.unifinancas.presentation.rest.orcamentometa;

import br.com.unifinancas.application.orcamentometa.OrcamentoMetaResumo;
import br.com.unifinancas.application.orcamentometa.OrcamentoMetaService;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.CriarOrcamentoMetaRequest;
import br.com.unifinancas.presentation.dto.OrcamentoMetaResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SAD §7.2, RF-12/RF-13. */
@RestController
@RequestMapping("/v1/orcamentos-metas")
public class OrcamentoMetaController {

    private final OrcamentoMetaService orcamentoMetaService;

    public OrcamentoMetaController(OrcamentoMetaService orcamentoMetaService) {
        this.orcamentoMetaService = orcamentoMetaService;
    }

    /** SAD §7.2: lista com progresso calculado (RN-10). */
    @GetMapping
    public List<OrcamentoMetaResponse> listar(TenantContext contexto) {
        return orcamentoMetaService.listarComProgresso(contexto).stream().map(this::paraResponse).toList();
    }

    /** SAD §7.2: cria Orcamento ou Meta, conforme {@code tipo}. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrcamentoMetaResponse criar(TenantContext contexto, @Valid @RequestBody CriarOrcamentoMetaRequest request) {
        OrcamentoMetaResumo resumo = switch (request.tipo()) {
            case ORCAMENTO -> orcamentoMetaService.criarOrcamento(contexto, request.categoriaId(),
                    request.valorLimite(), request.limiarAlertaPercentual(), request.dataInicio(), request.dataFim());
            case META -> orcamentoMetaService.criarMeta(
                    contexto, request.nome(), request.valorAlvo(), request.contaBancariaId(), request.dataInicio(), request.dataFim());
        };
        return paraResponse(resumo);
    }

    private OrcamentoMetaResponse paraResponse(OrcamentoMetaResumo resumo) {
        return new OrcamentoMetaResponse(resumo.id(), resumo.tipo(), resumo.categoriaId(), resumo.nome(),
                resumo.valorLimite(), resumo.valorAlvo(), resumo.periodo(), resumo.dataInicio(), resumo.dataFim(),
                resumo.contaBancariaId(), resumo.status(), resumo.valorAtual(), resumo.percentualAtingido(),
                resumo.limiarAlertaPercentual());
    }
}
