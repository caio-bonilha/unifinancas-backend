package br.com.unifinancas.domain.compra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** SAD Sec9.3: lista escopada ao ambiente - {@code ambienteFinanceiroId} e um parametro literal, mesmo criterio de {@code CartaoRepository}. */
public interface CompraRepository extends JpaRepository<Compra, UUID> {

    List<Compra> findAllByAmbienteFinanceiroId(UUID ambienteFinanceiroId);

    Optional<Compra> findByIdAndAmbienteFinanceiroId(UUID id, UUID ambienteFinanceiroId);
}
