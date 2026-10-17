package br.com.unifinancas.domain.ambientefinanceiro;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmbienteFinanceiroRepository extends JpaRepository<AmbienteFinanceiro, UUID> {
}
