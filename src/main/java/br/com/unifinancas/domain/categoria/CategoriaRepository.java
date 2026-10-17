package br.com.unifinancas.domain.categoria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** SAD Sec9.3: toda busca escopada ao ambiente - parametro literal, sem precisar de @RepositorioGlobal. */
public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {

    List<Categoria> findAllByAmbienteFinanceiroId(UUID ambienteFinanceiroId);

    /** Valida a categoria-pai (se informada) pertence ao mesmo ambiente - nunca aceitar um id de outro tenant. */
    Optional<Categoria> findByIdAndAmbienteFinanceiroId(UUID id, UUID ambienteFinanceiroId);
}
