package br.com.unifinancas.domain.ambientefinanceiro;

import br.com.unifinancas.domain.shared.RepositorioGlobal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConviteRepository extends JpaRepository<Convite, UUID> {

    @RepositorioGlobal(motivo = "O token de um convite e a unica coisa que o link de ativacao/aceite carrega "
            + "(quem recebe o convite ainda nao tem vinculo, e no caso de e-mail sem conta nem tem usuario ainda) "
            + "- por isso a busca e por token, nao por ambienteFinanceiroId.")
    Optional<Convite> findByTokenHash(String tokenHash);
}
