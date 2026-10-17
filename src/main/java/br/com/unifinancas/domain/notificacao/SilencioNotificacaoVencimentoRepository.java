package br.com.unifinancas.domain.notificacao;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SilencioNotificacaoVencimentoRepository extends JpaRepository<SilencioNotificacaoVencimento, UUID> {

    boolean existsByUsuarioIdAndLancamentoIdAndAmbienteFinanceiroId(
            UUID usuarioId, UUID lancamentoId, UUID ambienteFinanceiroId);

    List<SilencioNotificacaoVencimento> findAllByAmbienteFinanceiroIdAndLancamentoIdIn(
            UUID ambienteFinanceiroId, List<UUID> lancamentoIds);
}
