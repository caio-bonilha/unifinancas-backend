package br.com.unifinancas.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.unifinancas.domain.usuario.Usuario;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Valida contra um Postgres real (Testcontainers) as duas queries de bulk
 * update de {@link RefreshTokenRepository} - JPQL bulk update tem
 * comportamento de flush/cache que so um teste de integracao confirma de
 * verdade.
 */
class RefreshTokenRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void revogarFamiliaRevogaSoOsTokensDaquelaFamilia() {
        UUID usuario = criarUsuarioPersistido();
        UUID familiaAlvo = UUID.randomUUID();
        UUID outraFamilia = UUID.randomUUID();
        RefreshToken t1 = salvar(RefreshToken.novo(usuario, familiaAlvo, "hash-1", Instant.now().plusSeconds(60)));
        RefreshToken t2 = salvar(RefreshToken.novo(usuario, familiaAlvo, "hash-2", Instant.now().plusSeconds(60)));
        RefreshToken deOutraFamilia = salvar(RefreshToken.novo(usuario, outraFamilia, "hash-3", Instant.now().plusSeconds(60)));

        repository.revogarFamilia(familiaAlvo);
        entityManager.clear();

        assertThat(repository.findById(t1.getId()).orElseThrow().isRevogado()).isTrue();
        assertThat(repository.findById(t2.getId()).orElseThrow().isRevogado()).isTrue();
        assertThat(repository.findById(deOutraFamilia.getId()).orElseThrow().isRevogado()).isFalse();
    }

    @Test
    void revogarTodosDoUsuarioRevogaTodasAsFamiliasDaqueleUsuario() {
        UUID usuarioAlvo = criarUsuarioPersistido();
        UUID outroUsuario = criarUsuarioPersistido();
        RefreshToken doAlvo = salvar(RefreshToken.novo(usuarioAlvo, UUID.randomUUID(), "hash-a", Instant.now().plusSeconds(60)));
        RefreshToken deOutro = salvar(RefreshToken.novo(outroUsuario, UUID.randomUUID(), "hash-b", Instant.now().plusSeconds(60)));

        repository.revogarTodosDoUsuario(usuarioAlvo);
        entityManager.clear();

        assertThat(repository.findById(doAlvo.getId()).orElseThrow().isRevogado()).isTrue();
        assertThat(repository.findById(deOutro.getId()).orElseThrow().isRevogado()).isFalse();
    }

    private RefreshToken salvar(RefreshToken token) {
        return repository.saveAndFlush(token);
    }

    private UUID criarUsuarioPersistido() {
        Usuario usuario = Usuario.novo("Usuario Teste", UUID.randomUUID() + "@example.com", "hash");
        entityManager.persist(usuario);
        entityManager.flush();
        return usuario.getId();
    }
}
