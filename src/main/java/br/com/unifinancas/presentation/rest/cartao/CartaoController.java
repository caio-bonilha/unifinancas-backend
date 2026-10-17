package br.com.unifinancas.presentation.rest.cartao;

import br.com.unifinancas.application.cartao.CartaoResumo;
import br.com.unifinancas.application.cartao.CartaoService;
import br.com.unifinancas.application.fatura.FaturaResumo;
import br.com.unifinancas.application.fatura.FaturaService;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.AtualizarCartaoRequest;
import br.com.unifinancas.presentation.dto.AtualizarFaturaRequest;
import br.com.unifinancas.presentation.dto.CartaoResponse;
import br.com.unifinancas.presentation.dto.CriarCartaoRequest;
import br.com.unifinancas.presentation.dto.FaturaResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SAD §7.2, RF-17. Exige o header X-Ambiente-Financeiro-Id (nao esta na lista de excecoes do TenantContextFilter). */
@RestController
@RequestMapping("/v1/cartoes")
public class CartaoController {

    private final CartaoService cartaoService;
    private final FaturaService faturaService;

    public CartaoController(CartaoService cartaoService, FaturaService faturaService) {
        this.cartaoService = cartaoService;
        this.faturaService = faturaService;
    }

    @GetMapping
    public List<CartaoResponse> listar(TenantContext contexto) {
        return cartaoService.listar(contexto).stream().map(this::paraResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartaoResponse criar(TenantContext contexto, @Valid @RequestBody CriarCartaoRequest request) {
        CartaoResumo resumo = cartaoService.criar(contexto, request.contaBancariaId(), request.nome(), request.limite(),
                request.diaFechamento(), request.diaVencimento(), request.taxaJurosRotativoMensal());
        return paraResponse(resumo);
    }

    /** ADR-017: uma unica rota cobre editar campos, ativar e desativar (mesmo padrao de /v1/contas-bancarias). */
    @PatchMapping("/{id}")
    public CartaoResponse atualizar(TenantContext contexto, @PathVariable UUID id, @Valid @RequestBody AtualizarCartaoRequest request) {
        CartaoResumo resumo = switch (request.acao()) {
            case EDITAR -> {
                exigirCamposDeEdicao(request);
                yield cartaoService.editar(contexto, id, request.contaBancariaId(), request.nome(), request.limite(),
                        request.diaFechamento(), request.diaVencimento(), request.taxaJurosRotativoMensal());
            }
            case ATIVAR -> cartaoService.ativar(contexto, id);
            case DESATIVAR -> cartaoService.desativar(contexto, id);
        };
        return paraResponse(resumo);
    }

    /** RF-17, SAD §7.2: extrato de Faturas de um Cartao - mais recente primeiro, sem paginacao por cursor (poucas por Cartao). */
    @GetMapping("/{id}/faturas")
    public List<FaturaResponse> faturas(TenantContext contexto, @PathVariable UUID id) {
        return faturaService.listar(contexto, id).stream().map(this::paraResponse).toList();
    }

    /** RN-11, ADR-017: uma unica rota cobre pagar (total/parcial) e parcelar uma Fatura ATRASADA. */
    @PatchMapping("/{id}/faturas/{faturaId}")
    public FaturaResponse atualizarFatura(TenantContext contexto, @PathVariable UUID id, @PathVariable UUID faturaId,
            @Valid @RequestBody AtualizarFaturaRequest request) {
        FaturaResumo resumo = switch (request.acao()) {
            case PAGAR -> faturaService.pagar(contexto, id, faturaId, exigirValor(request), exigirDataPagamento(request));
            case PARCELAR -> faturaService.parcelar(contexto, id, faturaId, exigirNumeroParcelas(request));
        };
        return paraResponse(resumo);
    }

    private void exigirCamposDeEdicao(AtualizarCartaoRequest request) {
        if (request.nome() == null || request.limite() == null || request.diaFechamento() == null || request.diaVencimento() == null) {
            throw new RegraDeNegocioException("CAMPOS_OBRIGATORIOS_PARA_EDITAR",
                    "nome, limite, diaFechamento e diaVencimento sao obrigatorios para a acao EDITAR.");
        }
    }

    private BigDecimal exigirValor(AtualizarFaturaRequest request) {
        if (request.valor() == null) {
            throw new RegraDeNegocioException("VALOR_OBRIGATORIO", "valor e obrigatorio para a acao PAGAR.");
        }
        return request.valor();
    }

    private LocalDate exigirDataPagamento(AtualizarFaturaRequest request) {
        if (request.dataPagamento() == null) {
            throw new RegraDeNegocioException("DATA_PAGAMENTO_OBRIGATORIA", "dataPagamento e obrigatoria para a acao PAGAR.");
        }
        return request.dataPagamento();
    }

    private int exigirNumeroParcelas(AtualizarFaturaRequest request) {
        if (request.numeroParcelas() == null) {
            throw new RegraDeNegocioException("NUMERO_PARCELAS_OBRIGATORIO", "numeroParcelas e obrigatorio para a acao PARCELAR.");
        }
        return request.numeroParcelas();
    }

    private CartaoResponse paraResponse(CartaoResumo resumo) {
        return new CartaoResponse(resumo.id(), resumo.contaBancariaId(), resumo.nome(), resumo.limite(),
                resumo.diaFechamento(), resumo.diaVencimento(), resumo.ativo(), resumo.taxaJurosRotativoMensal());
    }

    private FaturaResponse paraResponse(FaturaResumo resumo) {
        return new FaturaResponse(resumo.id(), resumo.cartaoId(), resumo.competencia(), resumo.dataFechamento(),
                resumo.dataVencimento(), resumo.valorTotal(), resumo.valorPago(), resumo.status());
    }
}
