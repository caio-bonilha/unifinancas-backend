package br.com.unifinancas.domain.cartao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** SAD Sec9.3: lista escopada ao ambiente - {@code ambienteFinanceiroId} e um parametro literal, mesmo criterio de {@code ContaBancariaRepository}. */
public interface CartaoRepository extends JpaRepository<Cartao, UUID> {

    List<Cartao> findAllByAmbienteFinanceiroId(UUID ambienteFinanceiroId);

    Optional<Cartao> findByIdAndAmbienteFinanceiroId(UUID id, UUID ambienteFinanceiroId);
}
