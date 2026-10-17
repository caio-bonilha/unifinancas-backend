package br.com.unifinancas.infrastructure.persistence.jpa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaJpaRepository extends JpaRepository<Auditoria, UUID> {
}
