package br.com.unifinancas.application.relatorio;

import br.com.unifinancas.application.orcamentometa.OrcamentoMetaResumo;
import br.com.unifinancas.application.orcamentometa.OrcamentoMetaService;
import br.com.unifinancas.domain.categoria.Categoria;
import br.com.unifinancas.domain.categoria.CategoriaRepository;
import br.com.unifinancas.domain.contabancaria.ContaBancaria;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.OrigemLancamento;
import br.com.unifinancas.domain.lancamento.StatusLancamento;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrente;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrenteRepository;
import br.com.unifinancas.domain.movimentacao.Movimentacao;
import br.com.unifinancas.domain.movimentacao.MovimentacaoRepository;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

/**
 * RF-15, SAD Sec7.2: agregados do dashboard financeiro (T1.7.1). Agregados
 * do "periodo vigente" usam a mesma convencao ja estabelecida por {@code
 * OrcamentoMetaService} (RN-10): mes corrente. So leitura, sem {@code
 * @RequerPerfil} - visualizacao de dados financeiros e permitida a
 * qualquer perfil, inclusive VISUALIZADOR (Sec8.2).
 */
@Service
public class RelatorioService {

    private static final List<StatusLancamento> STATUS_PENDENTE_OU_ATRASADO = List.of(StatusLancamento.PENDENTE, StatusLancamento.ATRASADO);

    private final ContaBancariaRepository contaBancariaRepository;
    private final LancamentoRepository lancamentoRepository;
    private final CategoriaRepository categoriaRepository;
    private final OrcamentoMetaService orcamentoMetaService;
    private final MovimentacaoRepository movimentacaoRepository;
    private final LancamentoRecorrenteRepository lancamentoRecorrenteRepository;

    public RelatorioService(ContaBancariaRepository contaBancariaRepository, LancamentoRepository lancamentoRepository,
            CategoriaRepository categoriaRepository, OrcamentoMetaService orcamentoMetaService,
            MovimentacaoRepository movimentacaoRepository, LancamentoRecorrenteRepository lancamentoRecorrenteRepository) {
        this.contaBancariaRepository = contaBancariaRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.categoriaRepository = categoriaRepository;
        this.orcamentoMetaService = orcamentoMetaService;
        this.movimentacaoRepository = movimentacaoRepository;
        this.lancamentoRecorrenteRepository = lancamentoRecorrenteRepository;
    }

    public DashboardResumo obterDashboard(TenantContext contexto) {
        UUID ambienteId = contexto.ambienteFinanceiroId();
        LocalDate hoje = LocalDate.now();
        LocalDate inicioPeriodo = hoje.withDayOfMonth(1);
        LocalDate fimPeriodo = hoje.withDayOfMonth(hoje.lengthOfMonth());

        BigDecimal receitas = lancamentoRepository.somarPorTipoEPeriodo(ambienteId, TipoLancamento.RECEITA, inicioPeriodo, fimPeriodo);
        BigDecimal despesas = lancamentoRepository.somarPorTipoEPeriodo(ambienteId, TipoLancamento.DESPESA, inicioPeriodo, fimPeriodo);

        return new DashboardResumo(calcularSaldoTotal(ambienteId), receitas, despesas, receitas.subtract(despesas),
                calcularVencimentos(ambienteId), orcamentoMetaService.listarComProgresso(contexto),
                calcularDespesasPorCategoria(ambienteId, inicioPeriodo, fimPeriodo), inicioPeriodo, fimPeriodo);
    }

    /**
     * T2.4.1, RF-22, ADR-018: serie mensal dos ultimos 12 meses (o mes
     * corrente incluido, os 11 anteriores) - saldo total ao fim do mes,
     * receitas e despesas do mes. Mesma janela do relatorio de fluxo de
     * caixa projetado (T2.4.2) - "mesma regua" prospectiva/retrospectiva
     * (ADR-018).
     */
    public List<PontoEvolucaoMensal> obterEvolucao(TenantContext contexto) {
        UUID ambienteId = contexto.ambienteFinanceiroId();
        List<YearMonth> meses = ultimos12Meses();
        LocalDate inicio = meses.get(0).atDay(1);
        LocalDate fim = meses.get(meses.size() - 1).atEndOfMonth();

        List<BigDecimal> saldosPorMes = calcularSaldoPorMes(ambienteId, meses);
        Map<YearMonth, BigDecimal> receitasPorMes = agruparPorMes(
                lancamentoRepository.findAllByTipoEPeriodo(ambienteId, TipoLancamento.RECEITA, inicio, fim));
        Map<YearMonth, BigDecimal> despesasPorMes = agruparPorMes(
                lancamentoRepository.findAllByTipoEPeriodo(ambienteId, TipoLancamento.DESPESA, inicio, fim));

        List<PontoEvolucaoMensal> pontos = new ArrayList<>();
        for (int i = 0; i < meses.size(); i++) {
            YearMonth mes = meses.get(i);
            pontos.add(new PontoEvolucaoMensal(mes, saldosPorMes.get(i),
                    receitasPorMes.getOrDefault(mes, BigDecimal.ZERO), despesasPorMes.getOrDefault(mes, BigDecimal.ZERO)));
        }
        return pontos;
    }

    /** T2.4.1, RF-22: despesas por categoria de um periodo arbitrario (o Dashboard so oferece o mes corrente) - mesmo criterio de {@link #calcularDespesasPorCategoria}. */
    public List<GastoPorCategoria> obterDespesasPorCategoria(TenantContext contexto, LocalDate dataDe, LocalDate dataAte) {
        return calcularDespesasPorCategoria(contexto.ambienteFinanceiroId(), dataDe, dataAte);
    }

    /** T2.4.1, RF-22: receitas/despesas de um periodo arbitrario (o Dashboard so oferece o mes corrente). */
    public RelatorioReceitasDespesas obterReceitasEDespesas(TenantContext contexto, LocalDate dataDe, LocalDate dataAte) {
        UUID ambienteId = contexto.ambienteFinanceiroId();
        BigDecimal receitas = lancamentoRepository.somarPorTipoEPeriodo(ambienteId, TipoLancamento.RECEITA, dataDe, dataAte);
        BigDecimal despesas = lancamentoRepository.somarPorTipoEPeriodo(ambienteId, TipoLancamento.DESPESA, dataDe, dataAte);
        return new RelatorioReceitasDespesas(dataDe, dataAte, receitas, despesas, receitas.subtract(despesas));
    }

    /**
     * T2.4.2, RF-22, ADR-018: projecao de fluxo de caixa dos proximos 12
     * meses (mes corrente incluido) - soma, por mes, as ocorrencias
     * futuras de Lancamentos Recorrentes ativos ({@code
     * LancamentoRecorrente.ocorreEm}, dia a dia dentro da janela) com os
     * Lancamentos PENDENTE/ATRASADO ja existentes (nao originados de
     * recorrencia - essas ja entram pela regra de recorrencia acima,
     * somar os dois contaria a mesma coisa duas vezes).
     */
    public List<PontoFluxoCaixaProjetado> obterFluxoCaixaProjetado(TenantContext contexto) {
        UUID ambienteId = contexto.ambienteFinanceiroId();
        List<YearMonth> meses = proximos12Meses();
        LocalDate hoje = LocalDate.now();
        LocalDate fimJanela = meses.get(meses.size() - 1).atEndOfMonth();

        List<LancamentoRecorrente> ativas = lancamentoRecorrenteRepository.findAllByAmbienteFinanceiroId(ambienteId).stream()
                .filter(LancamentoRecorrente::isAtivo)
                .toList();
        Map<YearMonth, BigDecimal> receitasRecorrentes = projetarRecorrencias(ativas, TipoLancamento.RECEITA, hoje, fimJanela);
        Map<YearMonth, BigDecimal> despesasRecorrentes = projetarRecorrencias(ativas, TipoLancamento.DESPESA, hoje, fimJanela);

        Map<YearMonth, BigDecimal> receitasPendentes = agruparPorMes(lancamentoRepository
                .findAllByAmbienteFinanceiroIdAndTipoAndStatusInAndOrigemNotAndDataVencimentoBetween(
                        ambienteId, TipoLancamento.RECEITA, STATUS_PENDENTE_OU_ATRASADO, OrigemLancamento.RECORRENCIA, hoje, fimJanela));
        Map<YearMonth, BigDecimal> despesasPendentes = agruparPorMes(lancamentoRepository
                .findAllByAmbienteFinanceiroIdAndTipoAndStatusInAndOrigemNotAndDataVencimentoBetween(
                        ambienteId, TipoLancamento.DESPESA, STATUS_PENDENTE_OU_ATRASADO, OrigemLancamento.RECORRENCIA, hoje, fimJanela));

        List<PontoFluxoCaixaProjetado> pontos = new ArrayList<>();
        for (YearMonth mes : meses) {
            BigDecimal receitas = receitasRecorrentes.getOrDefault(mes, BigDecimal.ZERO)
                    .add(receitasPendentes.getOrDefault(mes, BigDecimal.ZERO));
            BigDecimal despesas = despesasRecorrentes.getOrDefault(mes, BigDecimal.ZERO)
                    .add(despesasPendentes.getOrDefault(mes, BigDecimal.ZERO));
            pontos.add(new PontoFluxoCaixaProjetado(mes, receitas, despesas, receitas.subtract(despesas)));
        }
        return pontos;
    }

    /** Varre dia a dia a janela de projecao (ate 366 dias) perguntando a cada Lancamento Recorrente ativo se ocorre naquele dia - volume pequeno (recorrencias por Ambiente pessoal), sem necessidade de otimizar. */
    private Map<YearMonth, BigDecimal> projetarRecorrencias(List<LancamentoRecorrente> recorrentes, TipoLancamento tipo,
            LocalDate inicio, LocalDate fim) {
        Map<YearMonth, BigDecimal> totalPorMes = new HashMap<>();
        List<LancamentoRecorrente> doTipo = recorrentes.stream().filter(recorrente -> recorrente.getTipo() == tipo).toList();
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            for (LancamentoRecorrente recorrente : doTipo) {
                if (recorrente.ocorreEm(dia)) {
                    totalPorMes.merge(YearMonth.from(dia), recorrente.getValor(), BigDecimal::add);
                }
            }
        }
        return totalPorMes;
    }

    /** Igual aos proximos 12 meses de {@link #ultimos12Meses()}, mas para frente - mes corrente incluido. */
    private List<YearMonth> proximos12Meses() {
        YearMonth mesCorrente = YearMonth.now();
        return IntStream.rangeClosed(0, 11).mapToObj(mesCorrente::plusMonths).toList();
    }

    private List<YearMonth> ultimos12Meses() {
        YearMonth mesCorrente = YearMonth.now();
        return IntStream.rangeClosed(0, 11).mapToObj(i -> mesCorrente.minusMonths(11 - i)).toList();
    }

    private Map<YearMonth, BigDecimal> agruparPorMes(List<LancamentoFinanceiro> lancamentos) {
        return lancamentos.stream().collect(Collectors.groupingBy(
                lancamento -> YearMonth.from(lancamento.getDataVencimento()),
                Collectors.reducing(BigDecimal.ZERO, LancamentoFinanceiro::getValor, BigDecimal::add)));
    }

    /**
     * T2.4.1, ADR-018: saldo (soma das Contas Bancarias ativas) ao FIM de
     * cada mes - reconstruido a partir de saldoInicial + soma acumulada das
     * Movimentacoes (RN-12) ate aquela data, nao um valor historico
     * armazenado (nao existe). Uma unica consulta busca todas as
     * Movimentacoes do Ambiente (ja existe, T1.11.4/LGPD) e acumula em
     * memoria, em vez de uma consulta agregada por mes (evita as "12
     * consultas por serie" que o ADR-018 pede para nao fazer).
     */
    private List<BigDecimal> calcularSaldoPorMes(UUID ambienteId, List<YearMonth> meses) {
        List<ContaBancaria> contas = contaBancariaRepository.findAllByAmbienteFinanceiroId(ambienteId);
        Set<UUID> contasAtivasIds = contas.stream().filter(ContaBancaria::isAtiva).map(ContaBancaria::getId).collect(Collectors.toSet());
        BigDecimal saldoInicialTotal = contas.stream().filter(ContaBancaria::isAtiva)
                .map(ContaBancaria::getSaldoInicial).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Movimentacao> movimentacoesOrdenadas = movimentacaoRepository.findAllByAmbienteFinanceiroId(ambienteId).stream()
                .filter(movimentacao -> contasAtivasIds.contains(movimentacao.getContaBancariaId()))
                .sorted(Comparator.comparing(Movimentacao::getData))
                .toList();

        List<BigDecimal> saldosPorMes = new ArrayList<>();
        BigDecimal acumulado = saldoInicialTotal;
        int indice = 0;
        for (YearMonth mes : meses) {
            LocalDate fimDoMes = mes.atEndOfMonth();
            while (indice < movimentacoesOrdenadas.size() && !movimentacoesOrdenadas.get(indice).getData().isAfter(fimDoMes)) {
                acumulado = acumulado.add(movimentacoesOrdenadas.get(indice).valorComSinal());
                indice++;
            }
            saldosPorMes.add(acumulado);
        }
        return saldosPorMes;
    }

    /** Soma o saldo derivado (Sec4.4) de todas as Contas Bancarias ativas do ambiente - contas encerradas nao compoem o saldo atual exibido. */
    private BigDecimal calcularSaldoTotal(UUID ambienteId) {
        return contaBancariaRepository.findAllByAmbienteFinanceiroId(ambienteId).stream()
                .filter(ContaBancaria::isAtiva)
                .map(ContaBancaria::getSaldoAtual)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ResumoVencimentos calcularVencimentos(UUID ambienteId) {
        long quantidadeAPagar = lancamentoRepository.countByAmbienteFinanceiroIdAndTipoAndStatusIn(
                ambienteId, TipoLancamento.DESPESA, STATUS_PENDENTE_OU_ATRASADO);
        BigDecimal valorAPagar = lancamentoRepository.somarPorTipoEStatus(
                ambienteId, TipoLancamento.DESPESA, STATUS_PENDENTE_OU_ATRASADO);
        long quantidadeAReceber = lancamentoRepository.countByAmbienteFinanceiroIdAndTipoAndStatusIn(
                ambienteId, TipoLancamento.RECEITA, STATUS_PENDENTE_OU_ATRASADO);
        BigDecimal valorAReceber = lancamentoRepository.somarPorTipoEStatus(
                ambienteId, TipoLancamento.RECEITA, STATUS_PENDENTE_OU_ATRASADO);
        return new ResumoVencimentos(quantidadeAPagar, valorAPagar, quantidadeAReceber, valorAReceber);
    }

    /**
     * `[Revisado — ADR-014]` soma os Lancamentos DESPESA (por dataVencimento)
     * junto das Movimentacoes avulsas de debito com categoria preenchida
     * (por data) - Movimentacao avulsa sem categoria fica de fora (T1.3b.5).
     */
    private List<GastoPorCategoria> calcularDespesasPorCategoria(UUID ambienteId, LocalDate inicioPeriodo, LocalDate fimPeriodo) {
        Map<UUID, String> nomePorCategoriaId = categoriaRepository.findAllByAmbienteFinanceiroId(ambienteId).stream()
                .collect(Collectors.toMap(Categoria::getId, Categoria::getNome));

        Map<UUID, BigDecimal> totalPorCategoriaId = new LinkedHashMap<>();
        lancamentoRepository.somarDespesasAgrupadasPorCategoria(ambienteId, inicioPeriodo, fimPeriodo)
                .forEach(linha -> acumular(totalPorCategoriaId, linha));
        movimentacaoRepository.somarAvulsasDebitoAgrupadasPorCategoria(ambienteId, inicioPeriodo, fimPeriodo)
                .forEach(linha -> acumular(totalPorCategoriaId, linha));

        return totalPorCategoriaId.entrySet().stream()
                .map(entrada -> new GastoPorCategoria(entrada.getKey(),
                        nomePorCategoriaId.getOrDefault(entrada.getKey(), "categoria removida"), entrada.getValue()))
                .toList();
    }

    private void acumular(Map<UUID, BigDecimal> totalPorCategoriaId, Object[] linha) {
        UUID categoriaId = (UUID) linha[0];
        BigDecimal valor = (BigDecimal) linha[1];
        totalPorCategoriaId.merge(categoriaId, valor, BigDecimal::add);
    }
}
