package br.com.unifinancas.domain.contabancaria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SAD Sec9.3: lista escopada ao ambiente - {@code ambienteFinanceiroId} e um
 * parametro literal, entao nao precisa de {@code @RepositorioGlobal}.
 */
public interface ContaBancariaRepository extends JpaRepository<ContaBancaria, UUID> {

    List<ContaBancaria> findAllByAmbienteFinanceiroId(UUID ambienteFinanceiroId);

    /** T1.3.3: valida que uma Conta Bancaria referenciada por um Lancamento pertence ao mesmo ambiente. */
    Optional<ContaBancaria> findByIdAndAmbienteFinanceiroId(UUID id, UUID ambienteFinanceiroId);
}
