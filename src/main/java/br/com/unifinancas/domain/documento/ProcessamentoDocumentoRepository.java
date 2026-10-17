package br.com.unifinancas.domain.documento;

import br.com.unifinancas.domain.shared.RepositorioGlobal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * T3.1.2/T3.1.5/T3.1.7: CRUD basico + consultas escopadas por tenant da API
 * de revisao/confirmacao. O worker (T3.1.4) usa {@code findById} (herdado) -
 * roda como job de sistema, sem tenant. UUID v7 (ADR-011) e quase-sequencial,
 * entao {@code OrderByIdDesc} aproxima "mais recente primeiro" sem precisar de
 * uma coluna de timestamp de criacao.
 */
public interface ProcessamentoDocumentoRepository extends JpaRepository<ProcessamentoDocumento, UUID> {

    /** SAD Sec9.3/Sec12.4: isolamento entre Ambientes Financeiros - documento_id e unico, mas o tenant e reafirmado aqui. */
    Optional<ProcessamentoDocumento> findByDocumentoIdAndAmbienteFinanceiroId(UUID documentoId, UUID ambienteFinanceiroId);

    /** T3.1.7: central de revisao - todos os processamentos do Ambiente Financeiro. */
    List<ProcessamentoDocumento> findAllByAmbienteFinanceiroIdOrderByIdDesc(UUID ambienteFinanceiroId);

    /** T3.1.7: central de revisao filtrada por status (ex.: AGUARDANDO_REVISAO, ERRO). */
    List<ProcessamentoDocumento> findAllByAmbienteFinanceiroIdAndStatusInOrderByIdDesc(
            UUID ambienteFinanceiroId, Collection<StatusProcessamento> status);

    /**
     * `[ADR-020]` Purga: apaga o registro de processamento 1:1 antes de a
     * {@code PurgaDocumentoJob} remover o Documento (a FK
     * {@code processamento_documento.documento_id} nao e ON DELETE CASCADE). Job
     * de sistema, cross-tenant — {@link RepositorioGlobal}.
     */
    @RepositorioGlobal(motivo = "Job diario de purga (T3.1.12) apaga o processamento junto com o Documento "
            + "soft-deletado fora da retencao - manutencao de sistema, nao uma operacao escopada a um tenant.")
    long deleteByDocumentoId(UUID documentoId);
}
