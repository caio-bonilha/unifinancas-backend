package br.com.unifinancas.domain.documento;

import br.com.unifinancas.domain.shared.RepositorioGlobal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoRepository extends JpaRepository<Documento, UUID> {

    /**
     * SAD Sec9.3/Sec12.4: leitura/escrita escopada ao tenant (vincular/desvincular,
     * excluir). `[ADR-020]` Ignora Documentos soft-deletados — para todo efeito de
     * negocio eles nao existem mais; a purga leva a linha embora depois.
     */
    Optional<Documento> findByIdAndAmbienteFinanceiroIdAndExcluidoEmIsNull(UUID id, UUID ambienteFinanceiroId);

    /** `[Revisado - Etapa B / ADR-020]` Documentos ativos de um Lancamento (N:1), mais recentes primeiro. */
    List<Documento> findAllByLancamentoIdAndAmbienteFinanceiroIdAndExcluidoEmIsNullOrderByCriadoEmDesc(UUID lancamentoId,
            UUID ambienteFinanceiroId);

    /**
     * `[ADR-020]` Varredura da purga: Documentos soft-deletados ha mais tempo que
     * a janela de retencao. E um job de sistema, roda sem tenant e varre todos os
     * Ambientes Financeiros de uma vez — {@link RepositorioGlobal} (mesmo padrao
     * de {@code LancamentoRecorrenteRepository.findAllByAtivo}).
     */
    @RepositorioGlobal(motivo = "Job diario de purga (T3.1.12) varre TODOS os Ambientes atras de Documentos "
            + "soft-deletados fora da retencao - manutencao de sistema, nao uma consulta escopada a um tenant.")
    List<Documento> findAllByExcluidoEmBefore(Instant limite);
}
