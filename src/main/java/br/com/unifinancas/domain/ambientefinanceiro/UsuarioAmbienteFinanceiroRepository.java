package br.com.unifinancas.domain.ambientefinanceiro;

import br.com.unifinancas.domain.shared.RepositorioGlobal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Porta de repositorio (SAD Sec9, "DDD tatico leve" - Sec11): estende
 * diretamente o Spring Data por pragmatismo, sem um adapter manual
 * separado. {@code findByUsuarioIdAndAmbienteFinanceiroId} satisfaz a regra
 * do ArchUnit (T0.2.1) porque o parametro se chama literalmente
 * ambienteFinanceiroId - validar um vinculo especifico e exatamente o tipo
 * de consulta escopada a um tenant.
 * {@code findAllByUsuarioIdAndStatus}, ao contrario, precisa de
 * {@code @RepositorioGlobal}: descobrir TODOS os ambientes de um usuario e
 * inerentemente cross-tenant (T1.1.7, RF-03 - e exatamente o proposito de
 * GET /v1/ambientes: listar antes de escolher qual usar).
 */
public interface UsuarioAmbienteFinanceiroRepository extends JpaRepository<UsuarioAmbienteFinanceiro, UUID> {

    Optional<UsuarioAmbienteFinanceiro> findByUsuarioIdAndAmbienteFinanceiroId(UUID usuarioId, UUID ambienteFinanceiroId);

    @RepositorioGlobal(motivo = "Listar todos os ambientes de um usuario (GET /v1/ambientes, RF-03) e "
            + "inerentemente cross-tenant - o proposito e descobrir os ambientes ANTES de escolher qual "
            + "usar, entao nao ha um ambienteFinanceiroId ainda para filtrar.")
    List<UsuarioAmbienteFinanceiro> findAllByUsuarioIdAndStatus(UUID usuarioId, StatusVinculo status);

    /** T1.1.8, RN "sempre ao menos um OWNER ativo": conta quantos OWNER ativos um ambiente tem. */
    long countByAmbienteFinanceiroIdAndPerfilAndStatus(UUID ambienteFinanceiroId, PerfilUsuarioAmbiente perfil,
            StatusVinculo status);

    /** T1.1.8: lista os membros (vinculos) de um Ambiente Financeiro para a tela/endpoint de gestao. */
    List<UsuarioAmbienteFinanceiro> findAllByAmbienteFinanceiroIdAndStatus(UUID ambienteFinanceiroId, StatusVinculo status);
}
