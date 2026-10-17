package br.com.unifinancas.presentation.rest.movimentacao;

import br.com.unifinancas.application.movimentacao.MovimentacaoResumo;
import br.com.unifinancas.application.movimentacao.MovimentacaoService;
import br.com.unifinancas.application.movimentacao.TransferenciaResumo;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.CriarMovimentacaoRequest;
import br.com.unifinancas.presentation.dto.CriarTransferenciaRequest;
import br.com.unifinancas.presentation.dto.EditarMovimentacaoRequest;
import br.com.unifinancas.presentation.dto.MovimentacaoResponse;
import br.com.unifinancas.presentation.dto.TransferenciaResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * SAD §7.2, ADR-012: cria uma Transferencia (RN-05) - duas Movimentacoes,
 * sem Lancamento. `[Revisado — ADR-014]` tambem cobre a Movimentacao
 * avulsa (criar/editar) e a exclusao unificada de qualquer Movimentacao.
 */
@RestController
@RequestMapping("/v1/movimentacoes")
public class MovimentacaoController {

    private final MovimentacaoService movimentacaoService;

    public MovimentacaoController(MovimentacaoService movimentacaoService) {
        this.movimentacaoService = movimentacaoService;
    }

    @PostMapping("/transferencias")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferenciaResponse criarTransferencia(TenantContext contexto, @Valid @RequestBody CriarTransferenciaRequest request) {
        TransferenciaResumo resumo = movimentacaoService.criarTransferencia(contexto, request.contaBancariaOrigemId(),
                request.contaBancariaDestinoId(), request.valor(), request.data(), request.descricao());
        return new TransferenciaResponse(paraResponse(resumo.origem()), paraResponse(resumo.destino()));
    }

    /** ADR-014: cria uma Movimentacao avulsa (origem=MANUAL). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovimentacaoResponse criar(TenantContext contexto, @Valid @RequestBody CriarMovimentacaoRequest request) {
        MovimentacaoResumo resumo = movimentacaoService.criar(contexto, request.contaBancariaId(), request.tipo(),
                request.valor(), request.data(), request.descricao(), request.categoriaId());
        return paraResponse(resumo);
    }

    /** ADR-014: so aceito quando a Movimentacao e avulsa - MOVIMENTACAO_NAO_EDITAVEL nas demais. */
    @PutMapping("/{id}")
    public MovimentacaoResponse editar(@PathVariable UUID id, TenantContext contexto,
            @Valid @RequestBody EditarMovimentacaoRequest request) {
        MovimentacaoResumo resumo = movimentacaoService.editar(contexto, id, request.tipo(), request.valor(),
                request.data(), request.descricao(), request.categoriaId());
        return paraResponse(resumo);
    }

    /**
     * ADR-014: exclusao unificada - varia por origem (ver
     * {@code MovimentacaoService.excluir}): avulsa apaga direto; vinculada a
     * Lancamento reabre o Lancamento; vinculada a Transferencia apaga o par
     * inteiro.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id, TenantContext contexto) {
        movimentacaoService.excluir(contexto, id);
    }

    private MovimentacaoResponse paraResponse(MovimentacaoResumo resumo) {
        return new MovimentacaoResponse(resumo.id(), resumo.contaBancariaId(), resumo.tipo(), resumo.valor(),
                resumo.data(), resumo.descricao(), resumo.origem(), resumo.categoriaId(), resumo.lancamentoId(),
                resumo.grupoTransferenciaId(), resumo.criadoEm());
    }
}
