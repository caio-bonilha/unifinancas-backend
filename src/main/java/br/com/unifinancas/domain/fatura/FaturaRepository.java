package br.com.unifinancas.domain.fatura;

import br.com.unifinancas.domain.shared.RepositorioGlobal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * SAD Sec9.3: Fatura nao tem {@code ambienteFinanceiroId} proprio (Sec5.2,
 * ADR-017) - isolamento por tenant passa por {@code JOIN} com {@code Cartao}
 * em toda consulta, nao por uma coluna direta.
 */
public interface FaturaRepository extends JpaRepository<Fatura, UUID> {

    @Query("SELECT f FROM Fatura f JOIN Cartao c ON c.id = f.cartaoId "
            + "WHERE f.id = :id AND c.ambienteFinanceiroId = :ambienteFinanceiroId")
    Optional<Fatura> findByIdAndAmbienteFinanceiroId(@Param("id") UUID id,
            @Param("ambienteFinanceiroId") UUID ambienteFinanceiroId);

    /** GET /v1/cartoes/{id}/faturas: mais recente primeiro (Sec7.2) - lista natural por Cartao, sem paginacao por cursor (poucas faturas por Cartao). */
    @Query("SELECT f FROM Fatura f JOIN Cartao c ON c.id = f.cartaoId "
            + "WHERE f.cartaoId = :cartaoId AND c.ambienteFinanceiroId = :ambienteFinanceiroId "
            + "ORDER BY f.competencia DESC")
    List<Fatura> findAllByCartaoIdAndAmbienteFinanceiroId(@Param("cartaoId") UUID cartaoId,
            @Param("ambienteFinanceiroId") UUID ambienteFinanceiroId);

    /** T2.1.2: a Fatura corrente de um Cartao (a ABERTA, no maximo uma por vez) - usado pelo job de fechamento, que varre TODOS os Ambientes (Sec12.6, mesmo criterio de VencimentoLancamentoJobHandler). */
    @RepositorioGlobal(motivo = "Job diario de fechamento de Fatura varre TODOS os Cartoes/Ambientes - manutencao de sistema, nao uma consulta escopada a um tenant especifico.")
    Optional<Fatura> findByCartaoIdAndStatus(UUID cartaoId, StatusFatura status);

    /** T2.2.2: a Fatura ABERTA de um Cartao, escopada ao ambiente - onde a 1a parcela de uma Compra no cartao sempre cai (CompraVendaService). */
    @Query("SELECT f FROM Fatura f JOIN Cartao c ON c.id = f.cartaoId "
            + "WHERE f.cartaoId = :cartaoId AND c.ambienteFinanceiroId = :ambienteFinanceiroId AND f.status = :status")
    Optional<Fatura> findByCartaoIdAndAmbienteFinanceiroIdAndStatus(@Param("cartaoId") UUID cartaoId,
            @Param("ambienteFinanceiroId") UUID ambienteFinanceiroId, @Param("status") StatusFatura status);

    /** T2.2.2: a Fatura de uma competencia especifica de um Cartao, escopada ao ambiente - CompraVendaService cria a Fatura sob demanda (Fatura.novoParaCompetencia) quando uma parcela cai numa competencia futura que ainda nao tem Fatura. */
    @Query("SELECT f FROM Fatura f JOIN Cartao c ON c.id = f.cartaoId "
            + "WHERE f.cartaoId = :cartaoId AND c.ambienteFinanceiroId = :ambienteFinanceiroId AND f.competencia = :competencia")
    Optional<Fatura> findByCartaoIdAndAmbienteFinanceiroIdAndCompetencia(@Param("cartaoId") UUID cartaoId,
            @Param("ambienteFinanceiroId") UUID ambienteFinanceiroId, @Param("competencia") LocalDate competencia);

    /** T2.1.2: Faturas FECHADA vencidas sem pagamento algum - o job marca como ATRASADA. */
    @RepositorioGlobal(motivo = "Job diario de fechamento de Fatura varre TODOS os Cartoes/Ambientes - manutencao de sistema, nao uma consulta escopada a um tenant especifico.")
    List<Fatura> findAllByStatusAndDataVencimentoBefore(StatusFatura status, LocalDate data);
}
