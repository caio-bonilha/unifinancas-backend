package br.com.unifinancas.infrastructure.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.unifinancas.application.notificacao.PlataformaDispositivo;
import br.com.unifinancas.domain.usuario.Usuario;
import br.com.unifinancas.domain.usuario.UsuarioRepository;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Valida contra um Postgres real (Testcontainers) o registro/reatribuicao de token de dispositivo (T1.5.6). */
class DispositivoPortJpaAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private DispositivoPortJpaAdapter adapter;

    @Autowired
    private DispositivoUsuarioRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void registrarPersisteUmNovoDispositivo() {
        Usuario usuario = usuarioRepository.save(Usuario.novo("Fulano", "fulano.it@example.com", "hash"));

        adapter.registrar(usuario.getId(), "token-abc", PlataformaDispositivo.ANDROID);

        List<DispositivoUsuario> encontrados = repository.findAllByUsuarioId(usuario.getId());
        assertThat(encontrados).hasSize(1);
        assertThat(encontrados.get(0).getToken()).isEqualTo("token-abc");
        assertThat(encontrados.get(0).getPlataforma()).isEqualTo(PlataformaDispositivo.ANDROID);
    }

    @Test
    void registrarOMesmoTokenDeNovoReatribuiEmVezDeDuplicar() {
        Usuario usuarioAntigo = usuarioRepository.save(Usuario.novo("Antigo", "antigo.it@example.com", "hash"));
        Usuario usuarioNovo = usuarioRepository.save(Usuario.novo("Novo", "novo.it@example.com", "hash"));
        adapter.registrar(usuarioAntigo.getId(), "token-compartilhado", PlataformaDispositivo.ANDROID);

        adapter.registrar(usuarioNovo.getId(), "token-compartilhado", PlataformaDispositivo.IOS);

        assertThat(repository.findAllByUsuarioId(usuarioAntigo.getId())).isEmpty();
        List<DispositivoUsuario> doNovo = repository.findAllByUsuarioId(usuarioNovo.getId());
        assertThat(doNovo).hasSize(1);
        assertThat(doNovo.get(0).getPlataforma()).isEqualTo(PlataformaDispositivo.IOS);
    }

    @Test
    void buscarTokensDoUsuarioRetornaSoOsDoUsuarioInformado() {
        Usuario usuario = usuarioRepository.save(Usuario.novo("Fulano2", "fulano2.it@example.com", "hash"));
        Usuario outro = usuarioRepository.save(Usuario.novo("Outro", "outro.it@example.com", "hash"));
        adapter.registrar(usuario.getId(), "token-1", PlataformaDispositivo.ANDROID);
        adapter.registrar(usuario.getId(), "token-2", PlataformaDispositivo.IOS);
        adapter.registrar(outro.getId(), "token-3", PlataformaDispositivo.ANDROID);

        List<String> tokens = adapter.buscarTokensDoUsuario(usuario.getId());

        assertThat(tokens).containsExactlyInAnyOrder("token-1", "token-2");
    }
}
