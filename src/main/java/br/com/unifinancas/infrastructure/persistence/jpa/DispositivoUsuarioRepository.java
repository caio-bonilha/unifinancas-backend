package br.com.unifinancas.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoUsuarioRepository extends JpaRepository<DispositivoUsuario, UUID> {

    Optional<DispositivoUsuario> findByToken(String token);

    List<DispositivoUsuario> findAllByUsuarioId(UUID usuarioId);
}
