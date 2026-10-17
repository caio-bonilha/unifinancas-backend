package br.com.unifinancas.presentation.rest.venda;

import br.com.unifinancas.application.compra.CompraVendaService;
import br.com.unifinancas.application.compra.ItemVendaEntrada;
import br.com.unifinancas.application.compra.VendaItemResumo;
import br.com.unifinancas.application.compra.VendaResumo;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.CriarVendaRequest;
import br.com.unifinancas.presentation.dto.ItemVendaRequest;
import br.com.unifinancas.presentation.dto.VendaItemResponse;
import br.com.unifinancas.presentation.dto.VendaResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * SAD §7.2, RF-19, T2.3.3. Sem PATCH/DELETE - mesmo motivo de
 * {@code CompraController} (editar depois de criada exigiria desfazer o
 * Lancamento ja gerado), embora aqui seja um caso ainda mais simples (um
 * unico Lancamento, sem Fatura envolvida).
 */
@RestController
@RequestMapping("/v1/vendas")
public class VendaController {

    private final CompraVendaService compraVendaService;

    public VendaController(CompraVendaService compraVendaService) {
        this.compraVendaService = compraVendaService;
    }

    @GetMapping
    public List<VendaResponse> listar(TenantContext contexto) {
        return compraVendaService.listarVendas(contexto).stream().map(this::paraResponse).toList();
    }

    @GetMapping("/{id}")
    public VendaResponse obter(TenantContext contexto, @PathVariable UUID id) {
        return paraResponse(compraVendaService.obterVenda(contexto, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VendaResponse criar(TenantContext contexto, @Valid @RequestBody CriarVendaRequest request) {
        List<ItemVendaEntrada> itens = request.itens().stream().map(this::paraEntrada).toList();
        VendaResumo resumo = compraVendaService.criarVenda(contexto, request.contatoId(), request.data(),
                request.formaPagamento(), request.documentoId(), request.descricao(), itens);
        return paraResponse(resumo);
    }

    private ItemVendaEntrada paraEntrada(ItemVendaRequest item) {
        return new ItemVendaEntrada(item.produtoNome(), item.quantidade(), item.valorUnitario());
    }

    private VendaResponse paraResponse(VendaResumo resumo) {
        List<VendaItemResponse> itens = resumo.itens().stream().map(this::paraResponse).toList();
        return new VendaResponse(resumo.id(), resumo.contatoId(), resumo.data(), resumo.valorTotal(),
                resumo.formaPagamento(), resumo.documentoId(), resumo.descricao(), itens);
    }

    private VendaItemResponse paraResponse(VendaItemResumo resumo) {
        return new VendaItemResponse(resumo.id(), resumo.produtoNome(), resumo.quantidade(), resumo.valorUnitario(), resumo.valorTotal());
    }
}
