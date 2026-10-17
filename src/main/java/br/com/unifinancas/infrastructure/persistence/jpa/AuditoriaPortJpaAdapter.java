package br.com.unifinancas.infrastructure.persistence.jpa;

import br.com.unifinancas.application.auditoria.AuditoriaPort;
import br.com.unifinancas.application.auditoria.RegistroAuditoria;
import org.springframework.stereotype.Component;

/** Implementacao JPA de {@link AuditoriaPort} (SAD Sec9.1). */
@Component
public class AuditoriaPortJpaAdapter implements AuditoriaPort {

    private final AuditoriaJpaRepository repository;

    public AuditoriaPortJpaAdapter(AuditoriaJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void registrar(RegistroAuditoria registro) {
        repository.save(Auditoria.novo(
                registro.ambienteFinanceiroId(),
                registro.usuarioId(),
                registro.entidade(),
                registro.entidadeId(),
                registro.acao(),
                registro.dadosAntes(),
                registro.dadosDepois()));
    }
}
