package br.com.unifinancas.presentation.rest.importacao;

import br.com.unifinancas.application.importacao.ImportacaoService;
import br.com.unifinancas.application.importacao.TipoArquivoPlanilha;
import br.com.unifinancas.application.importacao.TransacaoAnalisada;
import br.com.unifinancas.application.importacao.TransacaoConfirmada;
import br.com.unifinancas.application.importacao.TransacaoImportada;
import br.com.unifinancas.application.lancamento.LancamentoResumo;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.infrastructure.importacao.TabelaImportada;
import br.com.unifinancas.presentation.dto.AnalisarImportacaoRequest;
import br.com.unifinancas.presentation.dto.ConfirmarImportacaoRequest;
import br.com.unifinancas.presentation.dto.LancamentoResponse;
import br.com.unifinancas.presentation.dto.TabelaImportadaResponse;
import br.com.unifinancas.presentation.dto.TransacaoAnalisadaResponse;
import br.com.unifinancas.presentation.dto.TransacaoConfirmadaRequest;
import br.com.unifinancas.presentation.dto.TransacaoImportadaRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * SAD §7.2, RF-21, T4.1.4. Fluxo sem estado no backend (decisao consultada
 * com o usuario - ver {@code ImportacaoService}): quatro rotas, cada uma um
 * passo independente - o frontend guarda o resultado de um passo pra montar
 * o corpo do proximo.
 * <ul>
 *   <li>{@code POST .../ofx} - upload OFX, ja devolve analisado (sem coluna pra mapear).</li>
 *   <li>{@code POST .../tabela} - upload CSV/Excel, so devolve a tabela generica (cabecalho + linhas) pro usuario mapear.</li>
 *   <li>{@code POST .../analisar} - CSV/Excel: recebe a lista ja mapeada pelo frontend, devolve analisada.</li>
 *   <li>{@code POST /v1/importacoes} - confirma: cria os Lancamentos das transacoes que o usuario decidiu manter.</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/importacoes")
public class ImportacaoController {

    private final ImportacaoService importacaoService;

    public ImportacaoController(ImportacaoService importacaoService) {
        this.importacaoService = importacaoService;
    }

    @PostMapping(value = "/ofx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<TransacaoAnalisadaResponse> analisarOfx(TenantContext contexto, @RequestParam UUID contaBancariaId,
            @RequestParam MultipartFile arquivo) throws IOException {
        return importacaoService.analisarOfx(contexto, contaBancariaId, arquivo.getBytes()).stream().map(this::paraResponse).toList();
    }

    @PostMapping(value = "/tabela", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TabelaImportadaResponse lerTabela(TenantContext contexto, @RequestParam UUID contaBancariaId,
            @RequestParam TipoArquivoPlanilha tipoArquivo, @RequestParam MultipartFile arquivo) throws IOException {
        TabelaImportada tabela = importacaoService.lerTabela(contexto, contaBancariaId, tipoArquivo, arquivo.getBytes());
        return new TabelaImportadaResponse(tabela.cabecalho(), tabela.linhas());
    }

    @PostMapping("/analisar")
    public List<TransacaoAnalisadaResponse> analisar(TenantContext contexto, @Valid @RequestBody AnalisarImportacaoRequest request) {
        List<TransacaoImportada> transacoes = request.transacoes().stream().map(this::paraTransacaoImportada).toList();
        return importacaoService.analisar(contexto, request.contaBancariaId(), transacoes).stream().map(this::paraResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<LancamentoResponse> confirmar(TenantContext contexto, @Valid @RequestBody ConfirmarImportacaoRequest request) {
        List<TransacaoConfirmada> transacoes = request.transacoes().stream().map(this::paraTransacaoConfirmada).toList();
        return importacaoService.confirmar(contexto, request.contaBancariaId(), transacoes).stream().map(this::paraResponse).toList();
    }

    private TransacaoImportada paraTransacaoImportada(TransacaoImportadaRequest request) {
        return new TransacaoImportada(request.data(), request.valor(), request.descricao(), request.identificadorExterno());
    }

    private TransacaoConfirmada paraTransacaoConfirmada(TransacaoConfirmadaRequest request) {
        return new TransacaoConfirmada(request.data(), request.valor(), request.descricao(), request.categoriaId(),
                request.identificadorExterno());
    }

    private TransacaoAnalisadaResponse paraResponse(TransacaoAnalisada analisada) {
        return new TransacaoAnalisadaResponse(analisada.transacao().data(), analisada.transacao().valor(),
                analisada.transacao().descricao(), analisada.identificadorExternoResolvido(), analisada.duplicada(),
                analisada.lancamentoDuplicadoId(), analisada.categoriaSugeridaId());
    }

    private LancamentoResponse paraResponse(LancamentoResumo resumo) {
        return new LancamentoResponse(resumo.id(), resumo.tipo(), resumo.status(), resumo.valor(), resumo.dataEmissao(),
                resumo.dataVencimento(), resumo.dataPagamento(), resumo.categoriaId(), resumo.contatoId(),
                resumo.contaBancariaId(), resumo.formaPagamento(), resumo.origem(), resumo.descricao(),
                resumo.observacao(), resumo.linhaDigitavel());
    }
}
