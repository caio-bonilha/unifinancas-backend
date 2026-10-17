package br.com.unifinancas.domain.contato;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** SAD Sec9.3: lista escopada ao ambiente - parametro literal, sem precisar de @RepositorioGlobal. */
public interface ContatoRepository extends JpaRepository<Contato, UUID> {

    List<Contato> findAllByAmbienteFinanceiroId(UUID ambienteFinanceiroId);

    /** T1.3.3: valida que um Contato referenciado por um Lancamento pertence ao mesmo ambiente. */
    Optional<Contato> findByIdAndAmbienteFinanceiroId(UUID id, UUID ambienteFinanceiroId);
}
