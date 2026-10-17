package br.com.unifinancas.presentation.rest.relatorio;

import br.com.unifinancas.application.orcamentometa.OrcamentoMetaResumo;
import br.com.unifinancas.application.relatorio.DashboardResumo;
import br.com.unifinancas.application.relatorio.GastoPorCategoria;
import br.com.unifinancas.application.relatorio.RelatorioService;
import br.com.unifinancas.application.relatorio.ResumoVencimentos;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.DashboardResponse;
import br.com.unifinancas.presentation.dto.GastoPorCategoriaResponse;
import br.com.unifinancas.presentation.dto.OrcamentoMetaResponse;
import br.com.unifinancas.presentation.dto.ResumoVencimentosResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SAD §7.2, RF-15. */
@RestController
@RequestMapping("/v1/dashboard")
public class DashboardController {

    private final RelatorioService relatorioService;

    public DashboardController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    @GetMapping
    public DashboardResponse obter(TenantContext contexto) {
        DashboardResumo resumo = relatorioService.obterDashboard(contexto);
        return new DashboardResponse(resumo.saldoTotal(), resumo.receitasDoPeriodo(), resumo.despesasDoPeriodo(),
                resumo.resultadoDoPeriodo(), paraResponse(resumo.vencimentos()),
                resumo.orcamentos().stream().map(this::paraResponse).toList(),
                resumo.despesasPorCategoria().stream().map(this::paraResponse).toList(),
                resumo.inicioPeriodo(), resumo.fimPeriodo());
    }

    private ResumoVencimentosResponse paraResponse(ResumoVencimentos vencimentos) {
        return new ResumoVencimentosResponse(vencimentos.quantidadeContasAPagar(), vencimentos.valorContasAPagar(),
                vencimentos.quantidadeContasAReceber(), vencimentos.valorContasAReceber());
    }

    private OrcamentoMetaResponse paraResponse(OrcamentoMetaResumo resumo) {
        return new OrcamentoMetaResponse(resumo.id(), resumo.tipo(), resumo.categoriaId(), resumo.nome(),
                resumo.valorLimite(), resumo.valorAlvo(), resumo.periodo(), resumo.dataInicio(), resumo.dataFim(),
                resumo.contaBancariaId(), resumo.status(), resumo.valorAtual(), resumo.percentualAtingido(),
                resumo.limiarAlertaPercentual());
    }

    private GastoPorCategoriaResponse paraResponse(GastoPorCategoria gasto) {
        return new GastoPorCategoriaResponse(gasto.categoriaId(), gasto.categoriaNome(), gasto.valor());
    }
}
