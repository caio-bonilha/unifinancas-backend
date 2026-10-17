package br.com.unifinancas.domain.lancamentorecorrente;

import br.com.unifinancas.domain.shared.RepositorioGlobal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LancamentoRecorrenteRepository extends JpaRepository<LancamentoRecorrente, UUID> {

    List<LancamentoRecorrente> findAllByAmbienteFinanceiroId(UUID ambienteFinanceiroId);

    Optional<LancamentoRecorrente> findByIdAndAmbienteFinanceiroId(UUID id, UUID ambienteFinanceiroId);

    /**
     * T1.6.3, Fluxo 6.6: recorrencias ativas de TODOS os ambientes - varrida
     * pelo RecorrenciaJob diario. Cross-tenant de proposito
     * (@RepositorioGlobal): e um job de sistema, sem ambiente especifico
     * (Sec12.6), mesmo criterio de LancamentoRepository.findAllByStatusAndDataVencimentoBefore (T1.5.4).
     */
    @RepositorioGlobal(motivo = "Job diario de recorrencia (T1.6.3) varre TODOS os ambientes - e manutencao de "
            + "sistema, nao uma consulta escopada a um tenant especifico (Sec12.6).")
    List<LancamentoRecorrente> findAllByAtivo(boolean ativo);
}
