package br.com.unifinancas.infrastructure.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Reuso de um token ja revogado/rotacionado e sinal de furto - revoga a familia inteira (ADR-010). */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken r set r.revogado = true where r.familiaId = :familiaId")
    void revogarFamilia(@Param("familiaId") UUID familiaId);

    /** Usado no logout/troca de senha - invalida todas as sessoes do usuario. */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken r set r.revogado = true where r.usuarioId = :usuarioId")
    void revogarTodosDoUsuario(@Param("usuarioId") UUID usuarioId);
}
