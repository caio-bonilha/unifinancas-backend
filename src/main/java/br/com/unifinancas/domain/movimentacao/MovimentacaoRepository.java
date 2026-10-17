package br.com.unifinancas.domain.movimentacao;

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
 * SAD Sec9.3: toda busca escopada ao ambiente - parametro literal, mesmo
 * criterio de {@code LancamentoRepository}. {@code JpaSpecificationExecutor}
 * sustenta o extrato paginado por cursor da T1.3a.6 (SAD Sec7.2).
 */
public interface MovimentacaoRepository
        extends JpaRepository<Movimentacao, UUID>, JpaSpecificationExecutor<Movimentacao> {

    Optional<Movimentacao> findByIdAndAmbienteFinanceiroId(UUID id, UUID ambienteFinanceiroId);

    /** T1.11.4, RNF-09/LGPD: dump completo (sem paginacao, todas as contas) para a exportacao de dados de um Ambiente onde o usuario e OWNER. */
    List<Movimentacao> findAllByAmbienteFinanceiroId(UUID ambienteFinanceiroId);

    /** RN-12: a Movimentacao vinculada a um Lancamento liquidado - usada por REABRIR para removê-la. */
    Optional<Movimentacao> findByAmbienteFinanceiroIdAndLancamentoId(UUID ambienteFinanceiroId, UUID lancamentoId);

    /** ADR-014: as duas pernas de uma Transferencia (mesmo grupoTransferenciaId) - usada por excluir para apagar o par inteiro. */
    List<Movimentacao> findAllByAmbienteFinanceiroIdAndGrupoTransferenciaId(UUID ambienteFinanceiroId, UUID grupoTransferenciaId);

    /**
     * RN-12, T1.3a.4/T1.3a.5: soma com sinal (CREDITO soma, DEBITO subtrai)
     * de todas as Movimentacoes de uma Conta Bancaria - base do recalculo de
     * {@code saldoAtual} em {@code ContaBancariaService.recalcularSaldo},
     * que soma este resultado a {@code saldoInicial}.
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN m.tipo = br.com.unifinancas.domain.movimentacao.TipoMovimentacao.CREDITO "
            + "THEN m.valor ELSE -m.valor END), 0) FROM Movimentacao m "
            + "WHERE m.ambienteFinanceiroId = :ambienteFinanceiroId AND m.contaBancariaId = :contaBancariaId")
    BigDecimal somarComSinalPorConta(@Param("ambienteFinanceiroId") UUID ambienteFinanceiroId,
            @Param("contaBancariaId") UUID contaBancariaId);

    /**
     * ADR-014, T1.3b.5: soma por categoria das Movimentacoes avulsas
     * (origem=MANUAL) de debito com categoria preenchida, no periodo - usada
     * por {@code RelatorioService.calcularDespesasPorCategoria} para somar
     * junto dos Lancamentos (RelatorioService mescla os dois resultados por
     * categoriaId).
     */
    @Query("SELECT m.categoriaId, SUM(m.valor) FROM Movimentacao m "
            + "WHERE m.ambienteFinanceiroId = :ambienteFinanceiroId "
            + "AND m.origem = br.com.unifinancas.domain.movimentacao.OrigemMovimentacao.MANUAL "
            + "AND m.tipo = br.com.unifinancas.domain.movimentacao.TipoMovimentacao.DEBITO "
            + "AND m.data BETWEEN :inicio AND :fim AND m.categoriaId IS NOT NULL "
            + "GROUP BY m.categoriaId")
    List<Object[]> somarAvulsasDebitoAgrupadasPorCategoria(@Param("ambienteFinanceiroId") UUID ambienteFinanceiroId,
            @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /**
     * ADR-011, T1.3a.6: gera um UUID v7 para {@code grupoTransferenciaId},
     * usando a mesma funcao {@code uuidv7()} do banco que gera o id de toda
     * entidade (nao ha gerador de UUID v7 em Java neste projeto) -
     * compartilhado pelas duas Movimentacoes de uma Transferencia. Nao e
     * escopado a um tenant (so gera um valor) - @RepositorioGlobal.
     */
    @RepositorioGlobal(motivo = "So gera um UUID v7 via funcao do banco - nao consulta/grava dado de tenant algum.")
    @Query(value = "SELECT uuidv7()", nativeQuery = true)
    UUID gerarUuidV7();
}
