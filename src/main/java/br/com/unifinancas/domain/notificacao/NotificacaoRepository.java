package br.com.unifinancas.domain.notificacao;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID>, JpaSpecificationExecutor<Notificacao> {

    Optional<Notificacao> findByIdAndUsuarioIdAndAmbienteFinanceiroId(UUID id, UUID usuarioId, UUID ambienteFinanceiroId);
}
