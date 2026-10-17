package br.com.unifinancas.domain.venda;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** SAD Sec9.3: lista escopada ao ambiente - {@code ambienteFinanceiroId} e um parametro literal, mesmo criterio de {@code CompraRepository}. */
public interface VendaRepository extends JpaRepository<Venda, UUID> {

    List<Venda> findAllByAmbienteFinanceiroId(UUID ambienteFinanceiroId);

    Optional<Venda> findByIdAndAmbienteFinanceiroId(UUID id, UUID ambienteFinanceiroId);
}
