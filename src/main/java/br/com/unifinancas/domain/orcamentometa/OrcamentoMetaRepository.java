package br.com.unifinancas.domain.orcamentometa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** SAD Sec9.3: toda busca escopada ao ambiente - parametro literal, sem precisar de @RepositorioGlobal. */
public interface OrcamentoMetaRepository extends JpaRepository<OrcamentoMeta, UUID> {

    List<OrcamentoMeta> findAllByAmbienteFinanceiroId(UUID ambienteFinanceiroId);

    Optional<OrcamentoMeta> findByIdAndAmbienteFinanceiroId(UUID id, UUID ambienteFinanceiroId);

    /** T1.5.3, Fluxo 6.4: Orcamentos ATIVOs de uma Categoria - avaliados a cada Lancamento DESPESA novo. */
    List<OrcamentoMeta> findAllByAmbienteFinanceiroIdAndCategoriaIdAndTipoAndStatus(UUID ambienteFinanceiroId,
            UUID categoriaId, TipoOrcamentoMeta tipo, StatusOrcamentoMeta status);
}
