package br.com.unifinancas.infrastructure.persistence.jpa;

import br.com.unifinancas.application.notificacao.DispositivoPort;
import br.com.unifinancas.application.notificacao.PlataformaDispositivo;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Implementacao JPA de {@link DispositivoPort} (SAD Sec9.1, T1.5.6). */
@Component
public class DispositivoPortJpaAdapter implements DispositivoPort {

    private final DispositivoUsuarioRepository repository;

    public DispositivoPortJpaAdapter(DispositivoUsuarioRepository repository) {
        this.repository = repository;
    }

    @Override
    public void registrar(UUID usuarioId, String token, PlataformaDispositivo plataforma) {
        DispositivoUsuario dispositivo = repository.findByToken(token)
                .map(existente -> {
                    existente.reatribuir(usuarioId, plataforma);
                    return existente;
                })
                .orElseGet(() -> DispositivoUsuario.novo(usuarioId, token, plataforma));
        repository.save(dispositivo);
    }

    @Override
    public List<String> buscarTokensDoUsuario(UUID usuarioId) {
        return repository.findAllByUsuarioId(usuarioId).stream().map(DispositivoUsuario::getToken).toList();
    }
}
