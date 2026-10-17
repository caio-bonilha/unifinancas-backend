package br.com.unifinancas.presentation.rest.compra;

import br.com.unifinancas.application.compra.CompraItemResumo;
import br.com.unifinancas.application.compra.CompraResumo;
import br.com.unifinancas.application.compra.CompraVendaService;
import br.com.unifinancas.application.compra.ItemCompraEntrada;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.CompraItemResponse;
import br.com.unifinancas.presentation.dto.CompraResponse;
import br.com.unifinancas.presentation.dto.CriarCompraRequest;
import br.com.unifinancas.presentation.dto.ItemCompraRequest;
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
 * SAD §7.2, RF-18, T2.2.3. Sem PATCH/DELETE (decisao ja tomada no catalogo
 * de endpoints do SAD, que so lista GET/POST para Compras) - editar uma
 * Compra depois de criada exigiria desfazer os efeitos ja aplicados
 * (valor somado em Fatura(s), Lancamento(s) ja gerados), fora do escopo
 * desta tarefa.
 */
@RestController
@RequestMapping("/v1/compras")
public class CompraController {

    private final CompraVendaService compraVendaService;

    public CompraController(CompraVendaService compraVendaService) {
        this.compraVendaService = compraVendaService;
    }

    @GetMapping
    public List<CompraResponse> listar(TenantContext contexto) {
        return compraVendaService.listar(contexto).stream().map(this::paraResponse).toList();
    }

    @GetMapping("/{id}")
    public CompraResponse obter(TenantContext contexto, @PathVariable UUID id) {
        return paraResponse(compraVendaService.obter(contexto, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompraResponse criar(TenantContext contexto, @Valid @RequestBody CriarCompraRequest request) {
        List<ItemCompraEntrada> itens = request.itens().stream().map(this::paraEntrada).toList();
        CompraResumo resumo = compraVendaService.criar(contexto, request.contatoId(), request.data(),
                request.formaPagamento(), request.cartaoId(), request.parcelas(), request.documentoId(),
                request.descricao(), itens);
        return paraResponse(resumo);
    }

    private ItemCompraEntrada paraEntrada(ItemCompraRequest item) {
        return new ItemCompraEntrada(item.produtoNome(), item.quantidade(), item.valorUnitario());
    }

    private CompraResponse paraResponse(CompraResumo resumo) {
        List<CompraItemResponse> itens = resumo.itens().stream().map(this::paraResponse).toList();
        return new CompraResponse(resumo.id(), resumo.contatoId(), resumo.data(), resumo.valorTotal(),
                resumo.formaPagamento(), resumo.cartaoId(), resumo.parcelas(), resumo.documentoId(), resumo.descricao(), itens);
    }

    private CompraItemResponse paraResponse(CompraItemResumo resumo) {
        return new CompraItemResponse(resumo.id(), resumo.produtoNome(), resumo.quantidade(), resumo.valorUnitario(), resumo.valorTotal());
    }
}
