package br.com.unifinancas.domain.usuario;

import br.com.unifinancas.domain.shared.RepositorioGlobal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

@RepositorioGlobal(motivo = "Usuario e uma identidade global, independente de qualquer Ambiente Financeiro "
        + "(SAD Sec4.1) - buscar por e-mail (login, verificacao de unicidade no cadastro) e inerentemente "
        + "cross-tenant, nao ha ambienteFinanceiroId para filtrar.")
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);
}
