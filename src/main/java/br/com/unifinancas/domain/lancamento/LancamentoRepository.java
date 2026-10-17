package br.com.unifinancas.domain.lancamento;

import br.com.unifinancas.domain.shared.RepositorioGlobal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * SAD Sec9.3: toda busca escopada ao ambiente - parametro literal, sem
 * precisar de @RepositorioGlobal. {@code JpaSpecificationExecutor} sustenta
 * os filtros dinamicos (tipo/status/periodo) e o cursor da T1.3.5 - o
 * metodo herdado {@code findAll(Specification, Pageable)} nao e declarado
 * aqui (vem da biblioteca), entao o ArchUnit nem o analisa.
 */
public interface LancamentoRepository
        extends JpaRepository<LancamentoFinanceiro, UUID>, JpaSpecificationExecutor<LancamentoFinanceiro> {

    Optional<LancamentoFinanceiro> findByIdAndAmbienteFinanceiroId(UUID id, UUID ambienteFinanceiroId);

    /** ADR-015, T1.3c.4: localiza os Lancamentos de uma quitacao em lote - todos precisam pertencer ao ambiente. */
    List<LancamentoFinanceiro> findAllByIdInAndAmbienteFinanceiroId(List<UUID> ids, UUID ambienteFinanceiroId);

    /** T1.11.4, RNF-09/LGPD: dump completo (sem paginacao) para a exportacao de dados de um Ambiente onde o usuario e OWNER. */
    List<LancamentoFinanceiro> findAllByAmbienteFinanceiroId(UUID ambienteFinanceiroId);

    /**
     * T1.4.2, RN-10: soma dos Lancamentos DESPESA de uma Categoria no
     * periodo vigente - base do progresso de Orcamento. Conta qualquer
     * status exceto CANCELADO (nao so PAGO): RN-10 fala em "soma dos
     * Lancamentos DESPESA", sem o qualificador "confirmados" que a regra
     * do saldo derivado usa (Sec4.4/Sec13.5) - um gasto PENDENTE/ATRASADO
     * ja compromete o orcamento, o alerta (Fluxo 6.4) existe para avisar
     * ANTES do pagamento efetivo, nao so depois.
     */
    @Query("SELECT COALESCE(SUM(l.valor), 0) FROM LancamentoFinanceiro l "
            + "WHERE l.ambienteFinanceiroId = :ambienteFinanceiroId AND l.categoriaId = :categoriaId "
            + "AND l.tipo = br.com.unifinancas.domain.lancamento.TipoLancamento.DESPESA "
            + "AND l.status <> br.com.unifinancas.domain.lancamento.StatusLancamento.CANCELADO "
            + "AND l.dataVencimento BETWEEN :inicio AND :fim")
    BigDecimal somarDespesasPorCategoriaEPeriodo(@Param("ambienteFinanceiroId") UUID ambienteFinanceiroId,
            @Param("categoriaId") UUID categoriaId, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /**
     * T1.5.4, Sec6.5: Lancamentos vencidos ainda PENDENTE - varrido pelo job
     * diario para marcar ATRASADO. Cross-tenant de proposito
     * (@RepositorioGlobal): e um job de sistema, sem um ambiente especifico
     * (Sec12.6 - o worker reconstroi um TenantContext de sistema, sem
     * usuario associado).
     */
    @RepositorioGlobal(motivo = "Job diario de vencimento (T1.5.4) varre TODOS os ambientes - e manutencao de "
            + "sistema, nao uma consulta escopada a um tenant especifico (Sec12.6).")
    List<LancamentoFinanceiro> findAllByStatusAndDataVencimentoBefore(StatusLancamento status, LocalDate data);

    /** T1.5.4: Lancamentos PENDENTE de um ambiente vencendo dentro da janela de antecedencia configurada. */
    List<LancamentoFinanceiro> findAllByAmbienteFinanceiroIdAndStatusAndDataVencimentoBetween(
            UUID ambienteFinanceiroId, StatusLancamento status, LocalDate inicio, LocalDate fim);

    /**
     * T1.6.3, RNF-02: garante a idempotencia do RecorrenciaJob - cada par
     * (lancamentoRecorrenteId, dataVencimento) so pode gerar um Lancamento
     * (Fluxo 6.6). ambienteFinanceiroId exigido mesmo lancamentoRecorrenteId
     * ja escopando a um tenant - defesa em profundidade (Sec8.7/Sec12.4),
     * mesmo criterio de {@link #somarPorContaOrigem}.
     */
    boolean existsByAmbienteFinanceiroIdAndLancamentoRecorrenteIdAndDataVencimento(
            UUID ambienteFinanceiroId, UUID lancamentoRecorrenteId, LocalDate dataVencimento);

    /**
     * T1.7.1, RF-15: soma de receitas/despesas do periodo para o dashboard -
     * mesmo criterio de {@link #somarDespesasPorCategoriaEPeriodo} (qualquer
     * status exceto CANCELADO), generalizado para qualquer tipo e sem
     * filtro de categoria (agregado do ambiente inteiro).
     */
    @Query("SELECT COALESCE(SUM(l.valor), 0) FROM LancamentoFinanceiro l "
            + "WHERE l.ambienteFinanceiroId = :ambienteFinanceiroId AND l.tipo = :tipo "
            + "AND l.status <> br.com.unifinancas.domain.lancamento.StatusLancamento.CANCELADO "
            + "AND l.dataVencimento BETWEEN :inicio AND :fim")
    BigDecimal somarPorTipoEPeriodo(@Param("ambienteFinanceiroId") UUID ambienteFinanceiroId,
            @Param("tipo") TipoLancamento tipo, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /** T1.7.1, RF-15: valor total de contas a pagar/receber (PENDENTE/ATRASADO) para o resumo de vencimentos do dashboard, sem filtro de periodo. */
    @Query("SELECT COALESCE(SUM(l.valor), 0) FROM LancamentoFinanceiro l "
            + "WHERE l.ambienteFinanceiroId = :ambienteFinanceiroId AND l.tipo = :tipo AND l.status IN :status")
    BigDecimal somarPorTipoEStatus(@Param("ambienteFinanceiroId") UUID ambienteFinanceiroId,
            @Param("tipo") TipoLancamento tipo, @Param("status") List<StatusLancamento> status);

    /** T1.7.1, RF-15: mesma contagem de {@link #somarPorTipoEStatus}, para o resumo de vencimentos do dashboard. */
    long countByAmbienteFinanceiroIdAndTipoAndStatusIn(UUID ambienteFinanceiroId, TipoLancamento tipo, List<StatusLancamento> status);

    /** T1.7.1, RF-15: despesas do periodo agrupadas por categoria ("visao por categoria" do dashboard) - mesmo criterio de {@link #somarPorTipoEPeriodo}. */
    @Query("SELECT l.categoriaId, COALESCE(SUM(l.valor), 0) FROM LancamentoFinanceiro l "
            + "WHERE l.ambienteFinanceiroId = :ambienteFinanceiroId "
            + "AND l.tipo = br.com.unifinancas.domain.lancamento.TipoLancamento.DESPESA "
            + "AND l.status <> br.com.unifinancas.domain.lancamento.StatusLancamento.CANCELADO "
            + "AND l.dataVencimento BETWEEN :inicio AND :fim AND l.categoriaId IS NOT NULL "
            + "GROUP BY l.categoriaId")
    List<Object[]> somarDespesasAgrupadasPorCategoria(@Param("ambienteFinanceiroId") UUID ambienteFinanceiroId,
            @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /**
     * T2.4.1, RF-22, ADR-018: Lancamentos de um tipo no periodo (qualquer
     * status exceto CANCELADO, mesmo criterio de {@link #somarPorTipoEPeriodo})
     * - RelatorioService agrupa por mes em memoria para montar a evolucao
     * mensal, em vez de uma consulta agregada por mes (12 consultas por
     * serie) - volume de Lancamentos de um Ambiente pessoal numa janela de
     * 12 meses nao justifica a complexidade de agregacao por mes no banco.
     */
    @Query("SELECT l FROM LancamentoFinanceiro l WHERE l.ambienteFinanceiroId = :ambienteFinanceiroId "
            + "AND l.tipo = :tipo AND l.status <> br.com.unifinancas.domain.lancamento.StatusLancamento.CANCELADO "
            + "AND l.dataVencimento BETWEEN :inicio AND :fim")
    List<LancamentoFinanceiro> findAllByTipoEPeriodo(@Param("ambienteFinanceiroId") UUID ambienteFinanceiroId,
            @Param("tipo") TipoLancamento tipo, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /**
     * T2.4.2, RF-22, ADR-018: Lancamentos PENDENTE/ATRASADO ja existentes
     * (nao originados de recorrencia - essas ja entram na projecao pela
     * propria regra de recorrencia, {@code LancamentoRecorrente.ocorreEm})
     * com vencimento na janela de projecao.
     */
    List<LancamentoFinanceiro> findAllByAmbienteFinanceiroIdAndTipoAndStatusInAndOrigemNotAndDataVencimentoBetween(
            UUID ambienteFinanceiroId, TipoLancamento tipo, List<StatusLancamento> status, OrigemLancamento origem,
            LocalDate inicio, LocalDate fim);

    /**
     * T4.1.3, RF-21, RNF-02: um Lancamento ja importado com este
     * identificador de origem (FITID do OFX, ou o hash sintetico do
     * {@code ImportacaoService}) para esta Conta Bancaria - deteccao de
     * reimportacao do mesmo extrato, mesmo entre sessoes de importacao
     * separadas.
     */
    Optional<LancamentoFinanceiro> findByAmbienteFinanceiroIdAndContaBancariaIdAndIdentificadorExterno(
            UUID ambienteFinanceiroId, UUID contaBancariaId, String identificadorExterno);

    /**
     * T4.1.3, RF-21 (decisao consultada com o usuario): sugestao de
     * categoria por match exato de descricao normalizada - categorias mais
     * usadas primeiro (o chamador pega a primeira). {@code descricaoNormalizada}
     * ja vem em minusculas/sem espacos nas pontas de quem chama
     * ({@code ImportacaoService}); a comparacao aqui so normaliza o lado do
     * banco com o mesmo criterio (LOWER/TRIM), sem dobra de acentuacao
     * (exigiria extensao do Postgres - simplificacao deliberada).
     */
    @Query("SELECT l.categoriaId FROM LancamentoFinanceiro l "
            + "WHERE l.ambienteFinanceiroId = :ambienteFinanceiroId AND LOWER(TRIM(l.descricao)) = :descricaoNormalizada "
            + "AND l.categoriaId IS NOT NULL "
            + "GROUP BY l.categoriaId ORDER BY COUNT(l) DESC")
    List<UUID> categoriasPorDescricaoNormalizadaOrdenadasPorFrequencia(
            @Param("ambienteFinanceiroId") UUID ambienteFinanceiroId, @Param("descricaoNormalizada") String descricaoNormalizada);
}
