package br.com.unifinancas.application.relatorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.unifinancas.application.orcamentometa.OrcamentoMetaResumo;
import br.com.unifinancas.application.orcamentometa.OrcamentoMetaService;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.categoria.Categoria;
import br.com.unifinancas.domain.categoria.CategoriaRepository;
import br.com.unifinancas.domain.categoria.TipoCategoria;
import br.com.unifinancas.domain.contabancaria.ContaBancaria;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.contabancaria.TipoContaBancaria;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.StatusLancamento;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.lancamentorecorrente.FrequenciaRecorrencia;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrente;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrenteRepository;
import br.com.unifinancas.domain.movimentacao.Movimentacao;
import br.com.unifinancas.domain.movimentacao.MovimentacaoRepository;
import br.com.unifinancas.domain.movimentacao.TipoMovimentacao;
import br.com.unifinancas.domain.orcamentometa.PeriodoOrcamentoMeta;
import br.com.unifinancas.domain.orcamentometa.StatusOrcamentoMeta;
import br.com.unifinancas.domain.orcamentometa.TipoOrcamentoMeta;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RelatorioServiceTest {

    private final ContaBancariaRepository contaBancariaRepository = mock(ContaBancariaRepository.class);
    private final LancamentoRepository lancamentoRepository = mock(LancamentoRepository.class);
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final OrcamentoMetaService orcamentoMetaService = mock(OrcamentoMetaService.class);
    private final MovimentacaoRepository movimentacaoRepository = mock(MovimentacaoRepository.class);
    private final LancamentoRecorrenteRepository lancamentoRecorrenteRepository = mock(LancamentoRecorrenteRepository.class);
    private final RelatorioService service = new RelatorioService(contaBancariaRepository, lancamentoRepository,
            categoriaRepository, orcamentoMetaService, movimentacaoRepository, lancamentoRecorrenteRepository);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);
    private final LocalDate hoje = LocalDate.now();
    private final LocalDate inicioPeriodo = hoje.withDayOfMonth(1);
    private final LocalDate fimPeriodo = hoje.withDayOfMonth(hoje.lengthOfMonth());

    @Test
    void obterDashboardSomaSoAsContasAtivasNoSaldoTotal() {
        ContaBancaria ativa = ContaBancaria.novo(ambienteFinanceiroId, "Corrente", TipoContaBancaria.CORRENTE, new BigDecimal("1000.00"));
        ContaBancaria inativa = ContaBancaria.novo(ambienteFinanceiroId, "Antiga", TipoContaBancaria.CORRENTE, new BigDecimal("500.00"));
        ReflectionTestUtils.setField(inativa, "ativa", false);
        when(contaBancariaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of(ativa, inativa));
        stubsPadrao();

        DashboardResumo dashboard = service.obterDashboard(contexto);

        assertThat(dashboard.saldoTotal()).isEqualByComparingTo("1000.00");
    }

    @Test
    void obterDashboardCalculaResultadoComoReceitasMenosDespesas() {
        when(contaBancariaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of());
        when(lancamentoRepository.somarPorTipoEPeriodo(ambienteFinanceiroId, TipoLancamento.RECEITA, inicioPeriodo, fimPeriodo))
                .thenReturn(new BigDecimal("5000.00"));
        when(lancamentoRepository.somarPorTipoEPeriodo(ambienteFinanceiroId, TipoLancamento.DESPESA, inicioPeriodo, fimPeriodo))
                .thenReturn(new BigDecimal("3200.00"));
        stubsVencimentosEOutros();

        DashboardResumo dashboard = service.obterDashboard(contexto);

        assertThat(dashboard.receitasDoPeriodo()).isEqualByComparingTo("5000.00");
        assertThat(dashboard.despesasDoPeriodo()).isEqualByComparingTo("3200.00");
        assertThat(dashboard.resultadoDoPeriodo()).isEqualByComparingTo("1800.00");
        assertThat(dashboard.inicioPeriodo()).isEqualTo(inicioPeriodo);
        assertThat(dashboard.fimPeriodo()).isEqualTo(fimPeriodo);
    }

    @Test
    void obterDashboardMontaResumoDeVencimentos() {
        when(contaBancariaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of());
        List<StatusLancamento> pendenteOuAtrasado = List.of(StatusLancamento.PENDENTE, StatusLancamento.ATRASADO);
        when(lancamentoRepository.somarPorTipoEPeriodo(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(lancamentoRepository.countByAmbienteFinanceiroIdAndTipoAndStatusIn(ambienteFinanceiroId, TipoLancamento.DESPESA, pendenteOuAtrasado))
                .thenReturn(3L);
        when(lancamentoRepository.somarPorTipoEStatus(ambienteFinanceiroId, TipoLancamento.DESPESA, pendenteOuAtrasado))
                .thenReturn(new BigDecimal("450.00"));
        when(lancamentoRepository.countByAmbienteFinanceiroIdAndTipoAndStatusIn(ambienteFinanceiroId, TipoLancamento.RECEITA, pendenteOuAtrasado))
                .thenReturn(1L);
        when(lancamentoRepository.somarPorTipoEStatus(ambienteFinanceiroId, TipoLancamento.RECEITA, pendenteOuAtrasado))
                .thenReturn(new BigDecimal("2000.00"));
        when(lancamentoRepository.somarDespesasAgrupadasPorCategoria(eq(ambienteFinanceiroId), any(), any())).thenReturn(List.of());
        when(movimentacaoRepository.somarAvulsasDebitoAgrupadasPorCategoria(eq(ambienteFinanceiroId), any(), any()))
                .thenReturn(List.of());
        when(categoriaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of());
        when(orcamentoMetaService.listarComProgresso(contexto)).thenReturn(List.of());

        DashboardResumo dashboard = service.obterDashboard(contexto);

        assertThat(dashboard.vencimentos().quantidadeContasAPagar()).isEqualTo(3L);
        assertThat(dashboard.vencimentos().valorContasAPagar()).isEqualByComparingTo("450.00");
        assertThat(dashboard.vencimentos().quantidadeContasAReceber()).isEqualTo(1L);
        assertThat(dashboard.vencimentos().valorContasAReceber()).isEqualByComparingTo("2000.00");
    }

    @Test
    void obterDashboardResolveNomeDaCategoriaNaVisaoPorCategoria() {
        UUID categoriaId = UUID.randomUUID();
        Categoria categoria = Categoria.novo(ambienteFinanceiroId, "Alimentacao", TipoCategoria.DESPESA, null);
        ReflectionTestUtils.setField(categoria, "id", categoriaId);
        when(contaBancariaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of());
        when(lancamentoRepository.somarPorTipoEPeriodo(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(lancamentoRepository.somarPorTipoEStatus(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(lancamentoRepository.countByAmbienteFinanceiroIdAndTipoAndStatusIn(any(), any(), any())).thenReturn(0L);
        when(categoriaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of(categoria));
        when(lancamentoRepository.somarDespesasAgrupadasPorCategoria(eq(ambienteFinanceiroId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {categoriaId, new BigDecimal("300.00")}));
        when(movimentacaoRepository.somarAvulsasDebitoAgrupadasPorCategoria(eq(ambienteFinanceiroId), any(), any()))
                .thenReturn(List.of());
        when(orcamentoMetaService.listarComProgresso(contexto)).thenReturn(List.of());

        DashboardResumo dashboard = service.obterDashboard(contexto);

        assertThat(dashboard.despesasPorCategoria()).hasSize(1);
        assertThat(dashboard.despesasPorCategoria().get(0).categoriaNome()).isEqualTo("Alimentacao");
        assertThat(dashboard.despesasPorCategoria().get(0).valor()).isEqualByComparingTo("300.00");
    }

    @Test
    void obterDashboardIncluiOrcamentosComProgressoJaCalculado() {
        when(contaBancariaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of());
        when(lancamentoRepository.somarPorTipoEPeriodo(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(lancamentoRepository.somarPorTipoEStatus(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(lancamentoRepository.countByAmbienteFinanceiroIdAndTipoAndStatusIn(any(), any(), any())).thenReturn(0L);
        when(lancamentoRepository.somarDespesasAgrupadasPorCategoria(any(), any(), any())).thenReturn(List.of());
        when(movimentacaoRepository.somarAvulsasDebitoAgrupadasPorCategoria(any(), any(), any())).thenReturn(List.of());
        when(categoriaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of());
        OrcamentoMetaResumo orcamento = new OrcamentoMetaResumo(UUID.randomUUID(), TipoOrcamentoMeta.ORCAMENTO, UUID.randomUUID(),
                null, new BigDecimal("1000.00"), null, PeriodoOrcamentoMeta.MENSAL, hoje, null, null,
                StatusOrcamentoMeta.ATIVO, new BigDecimal("800.00"), new BigDecimal("80.00"), new BigDecimal("80.00"));
        when(orcamentoMetaService.listarComProgresso(contexto)).thenReturn(List.of(orcamento));

        DashboardResumo dashboard = service.obterDashboard(contexto);

        assertThat(dashboard.orcamentos()).containsExactly(orcamento);
    }

    private void stubsPadrao() {
        when(lancamentoRepository.somarPorTipoEPeriodo(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        stubsVencimentosEOutros();
    }

    private void stubsVencimentosEOutros() {
        when(lancamentoRepository.somarPorTipoEStatus(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(lancamentoRepository.countByAmbienteFinanceiroIdAndTipoAndStatusIn(any(), any(), any())).thenReturn(0L);
        when(lancamentoRepository.somarDespesasAgrupadasPorCategoria(any(), any(), any())).thenReturn(List.of());
        when(movimentacaoRepository.somarAvulsasDebitoAgrupadasPorCategoria(any(), any(), any())).thenReturn(List.of());
        when(categoriaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of());
        when(orcamentoMetaService.listarComProgresso(contexto)).thenReturn(List.of());
    }

    /** ADR-014, T1.3b.5: Movimentacao avulsa com categoria entra somada junto do Lancamento da mesma categoria. */
    @Test
    void obterDashboardSomaMovimentacaoAvulsaJuntoDoLancamentoNaMesmaCategoria() {
        UUID categoriaId = UUID.randomUUID();
        Categoria categoria = Categoria.novo(ambienteFinanceiroId, "Bancárias", TipoCategoria.DESPESA, null);
        ReflectionTestUtils.setField(categoria, "id", categoriaId);
        when(contaBancariaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of());
        when(lancamentoRepository.somarPorTipoEPeriodo(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(lancamentoRepository.somarPorTipoEStatus(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(lancamentoRepository.countByAmbienteFinanceiroIdAndTipoAndStatusIn(any(), any(), any())).thenReturn(0L);
        when(categoriaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of(categoria));
        when(lancamentoRepository.somarDespesasAgrupadasPorCategoria(eq(ambienteFinanceiroId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {categoriaId, new BigDecimal("300.00")}));
        when(movimentacaoRepository.somarAvulsasDebitoAgrupadasPorCategoria(eq(ambienteFinanceiroId), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {categoriaId, new BigDecimal("12.90")}));
        when(orcamentoMetaService.listarComProgresso(contexto)).thenReturn(List.of());

        DashboardResumo dashboard = service.obterDashboard(contexto);

        assertThat(dashboard.despesasPorCategoria()).hasSize(1);
        assertThat(dashboard.despesasPorCategoria().get(0).categoriaNome()).isEqualTo("Bancárias");
        assertThat(dashboard.despesasPorCategoria().get(0).valor()).isEqualByComparingTo("312.90");
    }

    /** T2.4.1, RF-22, ADR-018: evolucao mensal - 12 meses, saldo reconstruido a partir das Movimentacoes + receitas/despesas por mes. */
    @Test
    void obterEvolucaoRetorna12MesesComSaldoReceitasEDespesas() {
        ContaBancaria conta = ContaBancaria.novo(ambienteFinanceiroId, "Corrente", TipoContaBancaria.CORRENTE, new BigDecimal("100.00"));
        UUID contaId = UUID.randomUUID();
        ReflectionTestUtils.setField(conta, "id", contaId);
        when(contaBancariaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of(conta));

        YearMonth mesPassado = YearMonth.now().minusMonths(2);
        Movimentacao credito = Movimentacao.paraAvulsa(ambienteFinanceiroId, contaId, TipoMovimentacao.CREDITO,
                new BigDecimal("200.00"), mesPassado.atDay(10), "Salário", null);
        when(movimentacaoRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of(credito));

        LancamentoFinanceiro despesaMesCorrente = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, null, null, "Mercado", null);
        when(lancamentoRepository.findAllByTipoEPeriodo(eq(ambienteFinanceiroId), eq(TipoLancamento.RECEITA), any(), any()))
                .thenReturn(List.of());
        when(lancamentoRepository.findAllByTipoEPeriodo(eq(ambienteFinanceiroId), eq(TipoLancamento.DESPESA), any(), any()))
                .thenReturn(List.of(despesaMesCorrente));

        List<PontoEvolucaoMensal> evolucao = service.obterEvolucao(contexto);

        assertThat(evolucao).hasSize(12);
        assertThat(evolucao.get(11).mes()).isEqualTo(YearMonth.now());
        // saldo inicial (100) + credito de 200 no mes-2: os meses anteriores ao credito ficam so com o saldo inicial.
        assertThat(evolucao.get(0).saldo()).isEqualByComparingTo("100.00");
        assertThat(evolucao.get(11).saldo()).isEqualByComparingTo("300.00");
        assertThat(evolucao.get(11).despesas()).isEqualByComparingTo("50.00");
        assertThat(evolucao.get(11).receitas()).isEqualByComparingTo("0");
    }

    /** T2.4.1, RF-22: despesas por categoria de um periodo arbitrario - mesmo criterio do Dashboard, so que sem travar no mes corrente. */
    @Test
    void obterDespesasPorCategoriaUsaOPeriodoInformado() {
        UUID categoriaId = UUID.randomUUID();
        Categoria categoria = Categoria.novo(ambienteFinanceiroId, "Alimentacao", TipoCategoria.DESPESA, null);
        ReflectionTestUtils.setField(categoria, "id", categoriaId);
        LocalDate dataDe = LocalDate.of(2026, 1, 1);
        LocalDate dataAte = LocalDate.of(2026, 3, 31);
        when(categoriaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of(categoria));
        when(lancamentoRepository.somarDespesasAgrupadasPorCategoria(ambienteFinanceiroId, dataDe, dataAte))
                .thenReturn(List.<Object[]>of(new Object[] {categoriaId, new BigDecimal("500.00")}));
        when(movimentacaoRepository.somarAvulsasDebitoAgrupadasPorCategoria(ambienteFinanceiroId, dataDe, dataAte))
                .thenReturn(List.of());

        List<GastoPorCategoria> resultado = service.obterDespesasPorCategoria(contexto, dataDe, dataAte);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).categoriaNome()).isEqualTo("Alimentacao");
        assertThat(resultado.get(0).valor()).isEqualByComparingTo("500.00");
    }

    @Test
    void obterReceitasEDespesasCalculaOResultadoDoPeriodoInformado() {
        LocalDate dataDe = LocalDate.of(2026, 1, 1);
        LocalDate dataAte = LocalDate.of(2026, 3, 31);
        when(lancamentoRepository.somarPorTipoEPeriodo(ambienteFinanceiroId, TipoLancamento.RECEITA, dataDe, dataAte))
                .thenReturn(new BigDecimal("1000.00"));
        when(lancamentoRepository.somarPorTipoEPeriodo(ambienteFinanceiroId, TipoLancamento.DESPESA, dataDe, dataAte))
                .thenReturn(new BigDecimal("400.00"));

        RelatorioReceitasDespesas resultado = service.obterReceitasEDespesas(contexto, dataDe, dataAte);

        assertThat(resultado.receitas()).isEqualByComparingTo("1000.00");
        assertThat(resultado.despesas()).isEqualByComparingTo("400.00");
        assertThat(resultado.resultado()).isEqualByComparingTo("600.00");
        assertThat(resultado.inicioPeriodo()).isEqualTo(dataDe);
        assertThat(resultado.fimPeriodo()).isEqualTo(dataAte);
    }

    /** T2.4.2, RF-22, ADR-018: projecao soma ocorrencias futuras de recorrencias ativas + Lancamentos pendentes ja existentes (nao originados de recorrencia). */
    @Test
    void obterFluxoCaixaProjetadoSomaRecorrenciasEPendentesExistentes() {
        LancamentoRecorrente aluguel = LancamentoRecorrente.novo(ambienteFinanceiroId, "Aluguel", TipoLancamento.DESPESA,
                new BigDecimal("1500.00"), null, null, FrequenciaRecorrencia.MENSAL, hoje.getDayOfMonth(), hoje, null);
        when(lancamentoRecorrenteRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of(aluguel));

        LancamentoFinanceiro pendenteManual = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.RECEITA,
                new BigDecimal("300.00"), hoje, hoje, null, null, null, null, "Reembolso", null);
        when(lancamentoRepository.findAllByAmbienteFinanceiroIdAndTipoAndStatusInAndOrigemNotAndDataVencimentoBetween(
                        eq(ambienteFinanceiroId), eq(TipoLancamento.RECEITA), any(), any(), any(), any()))
                .thenReturn(List.of(pendenteManual));
        when(lancamentoRepository.findAllByAmbienteFinanceiroIdAndTipoAndStatusInAndOrigemNotAndDataVencimentoBetween(
                        eq(ambienteFinanceiroId), eq(TipoLancamento.DESPESA), any(), any(), any(), any()))
                .thenReturn(List.of());

        List<PontoFluxoCaixaProjetado> projecao = service.obterFluxoCaixaProjetado(contexto);

        assertThat(projecao).hasSize(12);
        assertThat(projecao.get(0).mes()).isEqualTo(YearMonth.now());
        assertThat(projecao.get(0).despesasProjetadas()).isEqualByComparingTo("1500.00");
        assertThat(projecao.get(0).receitasProjetadas()).isEqualByComparingTo("300.00");
        assertThat(projecao.get(0).resultadoProjetado()).isEqualByComparingTo("-1200.00");
        // meses seguintes continuam com a despesa recorrente do aluguel (mensal), sem a receita pontual.
        assertThat(projecao.get(1).despesasProjetadas()).isEqualByComparingTo("1500.00");
        assertThat(projecao.get(1).receitasProjetadas()).isEqualByComparingTo("0");
    }
}
