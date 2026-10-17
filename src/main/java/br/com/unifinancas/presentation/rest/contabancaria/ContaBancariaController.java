package br.com.unifinancas.presentation.rest.contabancaria;

import br.com.unifinancas.application.contabancaria.ContaBancariaResumo;
import br.com.unifinancas.application.contabancaria.ContaBancariaService;
import br.com.unifinancas.application.movimentacao.MovimentacaoResumo;
import br.com.unifinancas.application.movimentacao.MovimentacaoService;
import br.com.unifinancas.application.movimentacao.PaginaMovimentacoes;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.AtualizarContaBancariaRequest;
import br.com.unifinancas.presentation.dto.ContaBancariaResponse;
import br.com.unifinancas.presentation.dto.CriarContaBancariaRequest;
import br.com.unifinancas.presentation.dto.MovimentacaoResponse;
import br.com.unifinancas.presentation.dto.PaginaMovimentacoesResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SAD §7.2, RF-05. Exige o header X-Ambiente-Financeiro-Id (nao esta na lista de excecoes do TenantContextFilter). */
@RestController
@RequestMapping("/v1/contas-bancarias")
public class ContaBancariaController {

    private final ContaBancariaService contaBancariaService;
    private final MovimentacaoService movimentacaoService;

    public ContaBancariaController(ContaBancariaService contaBancariaService, MovimentacaoService movimentacaoService) {
        this.contaBancariaService = contaBancariaService;
        this.movimentacaoService = movimentacaoService;
    }

    @GetMapping
    public List<ContaBancariaResponse> listar(TenantContext contexto) {
        return contaBancariaService.listar(contexto).stream().map(this::paraResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContaBancariaResponse criar(TenantContext contexto, @Valid @RequestBody CriarContaBancariaRequest request) {
        ContaBancariaResumo resumo = contaBancariaService.criar(contexto, request.nome(), request.tipo(), request.saldoInicial());
        return paraResponse(resumo);
    }

    /** T1.9.6 (gap identificado ao planejar a tela): uma unica rota cobre editar, ativar e desativar. */
    @PatchMapping("/{id}")
    public ContaBancariaResponse atualizar(TenantContext contexto, @PathVariable UUID id,
            @Valid @RequestBody AtualizarContaBancariaRequest request) {
        ContaBancariaResumo resumo = switch (request.acao()) {
            case EDITAR -> {
                if (request.nome() == null || request.tipo() == null) {
                    throw new RegraDeNegocioException(
                            "CAMPOS_OBRIGATORIOS_PARA_EDITAR", "nome e tipo sao obrigatorios para a acao EDITAR.");
                }
                yield contaBancariaService.editar(contexto, id, request.nome(), request.tipo());
            }
            case ATIVAR -> contaBancariaService.ativar(contexto, id);
            case DESATIVAR -> contaBancariaService.desativar(contexto, id);
        };
        return paraResponse(resumo);
    }

    /** ADR-012, SAD §7.2: extrato da Conta Bancaria - Movimentacoes paginadas por cursor. */
    @GetMapping("/{id}/movimentacoes")
    public PaginaMovimentacoesResponse movimentacoes(TenantContext contexto, @PathVariable UUID id,
            @RequestParam(required = false) LocalDate dataDe, @RequestParam(required = false) LocalDate dataAte,
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        PaginaMovimentacoes pagina = movimentacaoService.extrato(contexto, id, dataDe, dataAte, cursor, limit);
        return new PaginaMovimentacoesResponse(pagina.itens().stream().map(this::paraResponse).toList(), pagina.proximoCursor());
    }

    private ContaBancariaResponse paraResponse(ContaBancariaResumo resumo) {
        return new ContaBancariaResponse(resumo.id(), resumo.nome(), resumo.tipo(), resumo.saldoInicial(),
                resumo.saldoAtual(), resumo.moeda(), resumo.ativa());
    }

    private MovimentacaoResponse paraResponse(MovimentacaoResumo resumo) {
        return new MovimentacaoResponse(resumo.id(), resumo.contaBancariaId(), resumo.tipo(), resumo.valor(),
                resumo.data(), resumo.descricao(), resumo.origem(), resumo.categoriaId(), resumo.lancamentoId(),
                resumo.grupoTransferenciaId(), resumo.criadoEm());
    }
}
