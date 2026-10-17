package br.com.unifinancas.presentation.rest.lancamento;

import br.com.unifinancas.application.lancamento.LancamentoResumo;
import br.com.unifinancas.application.lancamento.LancamentoService;
import br.com.unifinancas.application.lancamento.PaginaLancamentos;
import br.com.unifinancas.application.notificacao.SilencioNotificacaoVencimentoService;
import br.com.unifinancas.domain.lancamento.StatusLancamento;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.AtualizarLancamentoRequest;
import br.com.unifinancas.presentation.dto.CriarLancamentoRequest;
import br.com.unifinancas.presentation.dto.LancamentoResponse;
import br.com.unifinancas.presentation.dto.PaginaLancamentosResponse;
import br.com.unifinancas.presentation.dto.QuitacaoEmLoteRequest;
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

/**
 * SAD §7.2, RF-06/RF-07/RF-08. As "visoes" de contas a pagar/receber (RN-06)
 * nao tem rota propria - sao so chamadas particulares deste mesmo GET com
 * {@code tipo=DESPESA|RECEITA&status=PENDENTE,ATRASADO}, exatamente como o
 * catalogo de endpoints do SAD documenta.
 */
@RestController
@RequestMapping("/v1/lancamentos")
public class LancamentoController {

    private final LancamentoService lancamentoService;
    private final SilencioNotificacaoVencimentoService silencioNotificacaoVencimentoService;

    public LancamentoController(LancamentoService lancamentoService,
            SilencioNotificacaoVencimentoService silencioNotificacaoVencimentoService) {
        this.lancamentoService = lancamentoService;
        this.silencioNotificacaoVencimentoService = silencioNotificacaoVencimentoService;
    }

    @GetMapping
    public PaginaLancamentosResponse listar(TenantContext contexto,
            @RequestParam(required = false) List<TipoLancamento> tipo,
            @RequestParam(required = false) List<StatusLancamento> status,
            @RequestParam(required = false) LocalDate dataVencimentoDe,
            @RequestParam(required = false) LocalDate dataVencimentoAte,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        PaginaLancamentos pagina =
                lancamentoService.listar(contexto, tipo, status, dataVencimentoDe, dataVencimentoAte, cursor, limit);
        return new PaginaLancamentosResponse(pagina.itens().stream().map(this::paraResponse).toList(), pagina.proximoCursor());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LancamentoResponse criar(TenantContext contexto, @Valid @RequestBody CriarLancamentoRequest request) {
        LancamentoResumo resumo = lancamentoService.criar(contexto, request.tipo(), request.valor(),
                request.dataEmissao(), request.dataVencimento(), request.categoriaId(), request.contatoId(),
                request.contaBancariaId(), request.formaPagamento(), request.descricao(), request.observacao(),
                request.linhaDigitavel());
        return paraResponse(resumo);
    }

    /** SAD §7.2: uma unica rota cobre editar campos, confirmar pagamento/recebimento ou cancelar (T1.3.3). */
    @PatchMapping("/{id}")
    public LancamentoResponse atualizar(TenantContext contexto, @PathVariable UUID id,
            @Valid @RequestBody AtualizarLancamentoRequest request) {
        LancamentoResumo resumo = switch (request.acao()) {
            case EDITAR -> {
                exigirCamposDeEdicao(request);
                yield lancamentoService.editar(contexto, id, request.valor(), request.dataEmissao(),
                        request.dataVencimento(), request.categoriaId(), request.contatoId(), request.contaBancariaId(),
                        request.formaPagamento(), request.descricao(), request.observacao(), request.linhaDigitavel());
            }
            case CONFIRMAR_PAGAMENTO -> lancamentoService.confirmarPagamento(contexto, id, exigirDataPagamento(request),
                    request.contaBancariaId(), request.formaPagamento());
            case CONFIRMAR_RECEBIMENTO -> lancamentoService.confirmarRecebimento(contexto, id, exigirDataPagamento(request),
                    request.contaBancariaId(), request.formaPagamento());
            case CANCELAR -> lancamentoService.cancelar(contexto, id);
            case REABRIR -> lancamentoService.reabrir(contexto, id);
        };
        return paraResponse(resumo);
    }

    /** ADR-015: confirma pagamento/recebimento de varios Lancamentos de uma vez, todos do mesmo tipo. */
    @PatchMapping("/quitacao-em-lote")
    public List<LancamentoResponse> quitarEmLote(TenantContext contexto, @Valid @RequestBody QuitacaoEmLoteRequest request) {
        return lancamentoService
                .quitarEmLote(contexto, request.ids(), request.dataPagamento(), request.contaBancariaId(), request.formaPagamento())
                .stream().map(this::paraResponse).toList();
    }

    /** T1.5.7: cada usuario decide, por conta propria, parar de receber o aviso de vencimento proximo deste Lancamento. */
    @PostMapping("/{id}/silenciar-notificacao-vencimento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void silenciarNotificacaoVencimento(TenantContext contexto, @PathVariable UUID id) {
        silencioNotificacaoVencimentoService.silenciar(contexto, id);
    }

    private void exigirCamposDeEdicao(AtualizarLancamentoRequest request) {
        if (request.valor() == null || request.dataEmissao() == null || request.dataVencimento() == null
                || request.descricao() == null || request.descricao().isBlank()) {
            throw new RegraDeNegocioException("CAMPOS_OBRIGATORIOS_PARA_EDITAR",
                    "valor, dataEmissao, dataVencimento e descricao sao obrigatorios para a acao EDITAR.");
        }
    }

    private LocalDate exigirDataPagamento(AtualizarLancamentoRequest request) {
        if (request.dataPagamento() == null) {
            throw new RegraDeNegocioException("DATA_PAGAMENTO_OBRIGATORIA",
                    "dataPagamento e obrigatoria para confirmar pagamento ou recebimento.");
        }
        return request.dataPagamento();
    }

    private LancamentoResponse paraResponse(LancamentoResumo resumo) {
        return new LancamentoResponse(resumo.id(), resumo.tipo(), resumo.status(), resumo.valor(),
                resumo.dataEmissao(), resumo.dataVencimento(), resumo.dataPagamento(), resumo.categoriaId(),
                resumo.contatoId(), resumo.contaBancariaId(),
                resumo.formaPagamento(), resumo.origem(), resumo.descricao(), resumo.observacao(),
                resumo.linhaDigitavel());
    }
}
