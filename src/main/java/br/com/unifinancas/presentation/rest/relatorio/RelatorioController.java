package br.com.unifinancas.presentation.rest.relatorio;

import br.com.unifinancas.application.relatorio.GastoPorCategoria;
import br.com.unifinancas.application.relatorio.PontoEvolucaoMensal;
import br.com.unifinancas.application.relatorio.PontoFluxoCaixaProjetado;
import br.com.unifinancas.application.relatorio.RelatorioReceitasDespesas;
import br.com.unifinancas.application.relatorio.RelatorioService;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.GastoPorCategoriaResponse;
import br.com.unifinancas.presentation.dto.PontoEvolucaoMensalResponse;
import br.com.unifinancas.presentation.dto.PontoFluxoCaixaProjetadoResponse;
import br.com.unifinancas.presentation.dto.RelatorioReceitasDespesasResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SAD §7.2, RF-22, T2.4.3. So leitura, sem {@code @RequerPerfil} - mesmo
 * criterio de {@code DashboardController} (visualizacao de dados financeiros
 * e permitida a qualquer perfil, inclusive VISUALIZADOR, Sec8.2).
 */
@RestController
@RequestMapping("/v1/relatorios")
public class RelatorioController {

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    /** ADR-018: serie mensal dos ultimos 12 meses (saldo, receitas, despesas) - sem parametros. */
    @GetMapping("/evolucao")
    public List<PontoEvolucaoMensalResponse> evolucao(TenantContext contexto) {
        return relatorioService.obterEvolucao(contexto).stream().map(this::paraResponse).toList();
    }

    /** Despesas por categoria de um periodo arbitrario - {@code dataDe}/{@code dataAte} ausentes usam o mes corrente (mesma convencao do Dashboard). */
    @GetMapping("/categorias")
    public List<GastoPorCategoriaResponse> categorias(TenantContext contexto,
            @RequestParam(required = false) LocalDate dataDe, @RequestParam(required = false) LocalDate dataAte) {
        LocalDate inicio = dataDe != null ? dataDe : inicioDoMesCorrente();
        LocalDate fim = dataAte != null ? dataAte : fimDoMesCorrente();
        return relatorioService.obterDespesasPorCategoria(contexto, inicio, fim).stream().map(this::paraResponse).toList();
    }

    /** Receitas/despesas de um periodo arbitrario - mesma convencao de {@code dataDe}/{@code dataAte} de {@link #categorias}. */
    @GetMapping("/receitas-despesas")
    public RelatorioReceitasDespesasResponse receitasEDespesas(TenantContext contexto,
            @RequestParam(required = false) LocalDate dataDe, @RequestParam(required = false) LocalDate dataAte) {
        LocalDate inicio = dataDe != null ? dataDe : inicioDoMesCorrente();
        LocalDate fim = dataAte != null ? dataAte : fimDoMesCorrente();
        RelatorioReceitasDespesas resultado = relatorioService.obterReceitasEDespesas(contexto, inicio, fim);
        return new RelatorioReceitasDespesasResponse(resultado.inicioPeriodo(), resultado.fimPeriodo(),
                resultado.receitas(), resultado.despesas(), resultado.resultado());
    }

    /** ADR-018: serie mensal dos proximos 12 meses (receitas/despesas/resultado projetados) - sem parametros. */
    @GetMapping("/fluxo-caixa-projetado")
    public List<PontoFluxoCaixaProjetadoResponse> fluxoCaixaProjetado(TenantContext contexto) {
        return relatorioService.obterFluxoCaixaProjetado(contexto).stream().map(this::paraResponse).toList();
    }

    private LocalDate inicioDoMesCorrente() {
        return LocalDate.now().withDayOfMonth(1);
    }

    private LocalDate fimDoMesCorrente() {
        LocalDate hoje = LocalDate.now();
        return hoje.withDayOfMonth(hoje.lengthOfMonth());
    }

    private PontoEvolucaoMensalResponse paraResponse(PontoEvolucaoMensal ponto) {
        return new PontoEvolucaoMensalResponse(ponto.mes(), ponto.saldo(), ponto.receitas(), ponto.despesas());
    }

    private PontoFluxoCaixaProjetadoResponse paraResponse(PontoFluxoCaixaProjetado ponto) {
        return new PontoFluxoCaixaProjetadoResponse(
                ponto.mes(), ponto.receitasProjetadas(), ponto.despesasProjetadas(), ponto.resultadoProjetado());
    }

    private GastoPorCategoriaResponse paraResponse(GastoPorCategoria gasto) {
        return new GastoPorCategoriaResponse(gasto.categoriaId(), gasto.categoriaNome(), gasto.valor());
    }
}
