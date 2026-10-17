package br.com.unifinancas.domain.usuario;

import br.com.unifinancas.domain.shared.RepositorioGlobal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SAD Sec5.2 / Sec6.7 (T1.1.10). {@code redefinicao_senha} pertence a
 * identidade global do {@link Usuario}, nao a um Ambiente Financeiro (mesma
 * natureza de refresh_token) - a busca e por token ou por usuario, nunca por
 * tenant, por isso {@link RepositorioGlobal} no nivel da interface.
 */
@RepositorioGlobal(motivo = "redefinicao_senha e um mecanismo da identidade global do Usuario (SAD Sec5.2, "
        + "Sec6.7), sem vinculo com Ambiente Financeiro - a busca e por token_hash (o link de e-mail) ou "
        + "por usuario_id (invalidar os pendentes na troca), nao ha ambienteFinanceiroId para filtrar.")
public interface RedefinicaoSenhaRepository extends JpaRepository<RedefinicaoSenha, UUID> {

    Optional<RedefinicaoSenha> findByTokenHash(String tokenHash);

    /** Todos os tokens ainda nao consumidos de um Usuario - usados para invalida-los de uma vez ao redefinir a senha. */
    List<RedefinicaoSenha> findByUsuarioIdAndUsadoEmIsNull(UUID usuarioId);
}
