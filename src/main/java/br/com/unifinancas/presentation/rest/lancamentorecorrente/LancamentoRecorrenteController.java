package br.com.unifinancas.presentation.rest.lancamentorecorrente;

import br.com.unifinancas.application.lancamentorecorrente.LancamentoRecorrenteResumo;
import br.com.unifinancas.application.lancamentorecorrente.LancamentoRecorrenteService;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.AtualizarLancamentoRecorrenteRequest;
import br.com.unifinancas.presentation.dto.CriarLancamentoRecorrenteRequest;
import br.com.unifinancas.presentation.dto.LancamentoRecorrenteResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SAD §7.2, RF-11 (T1.6.2). */
@RestController
@RequestMapping("/v1/lancamentos-recorrentes")
public class LancamentoRecorrenteController {

    private final LancamentoRecorrenteService lancamentoRecorrenteService;

    public LancamentoRecorrenteController(LancamentoRecorrenteService lancamentoRecorrenteService) {
        this.lancamentoRecorrenteService = lancamentoRecorrenteService;
    }

    @GetMapping
    public List<LancamentoRecorrenteResponse> listar(TenantContext contexto) {
        return lancamentoRecorrenteService.listar(contexto).stream().map(this::paraResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LancamentoRecorrenteResponse criar(TenantContext contexto, @Valid @RequestBody CriarLancamentoRecorrenteRequest request) {
        LancamentoRecorrenteResumo resumo = lancamentoRecorrenteService.criar(contexto, request.descricao(), request.tipo(),
                request.valor(), request.categoriaId(), request.contaBancariaId(),
                request.frequencia(), request.diaReferencia(), request.dataInicio(), request.dataFim());
        return paraResponse(resumo);
    }

    /** SAD §7.2: uma unica rota cobre editar campos, desativar ou reativar (T1.6.2). */
    @PatchMapping("/{id}")
    public LancamentoRecorrenteResponse atualizar(TenantContext contexto, @PathVariable UUID id,
            @Valid @RequestBody AtualizarLancamentoRecorrenteRequest request) {
        LancamentoRecorrenteResumo resumo = switch (request.acao()) {
            case EDITAR -> {
                exigirCamposDeEdicao(request);
                yield lancamentoRecorrenteService.editar(contexto, id, request.descricao(), request.valor(),
                        request.categoriaId(), request.contaBancariaId(),
                        request.frequencia(), request.diaReferencia(), request.dataInicio(), request.dataFim());
            }
            case DESATIVAR -> lancamentoRecorrenteService.desativar(contexto, id);
            case REATIVAR -> lancamentoRecorrenteService.reativar(contexto, id);
        };
        return paraResponse(resumo);
    }

    private void exigirCamposDeEdicao(AtualizarLancamentoRecorrenteRequest request) {
        if (request.descricao() == null || request.valor() == null || request.frequencia() == null
                || request.dataInicio() == null) {
            throw new RegraDeNegocioException("CAMPOS_OBRIGATORIOS_PARA_EDITAR",
                    "descricao, valor, frequencia e dataInicio sao obrigatorios para a acao EDITAR.");
        }
    }

    private LancamentoRecorrenteResponse paraResponse(LancamentoRecorrenteResumo resumo) {
        return new LancamentoRecorrenteResponse(resumo.id(), resumo.descricao(), resumo.tipo(), resumo.valor(),
                resumo.categoriaId(), resumo.contaBancariaId(), resumo.frequencia(),
                resumo.diaReferencia(), resumo.dataInicio(), resumo.dataFim(), resumo.ativo());
    }
}
