package br.com.unifinancas.domain.usuario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UsuarioTest {

    @Test
    void novoUsuarioNasceAtivoComOsDadosInformados() {
        Usuario usuario = Usuario.novo("Maria Silva", "maria@example.com", "hash-da-senha");

        assertThat(usuario.getNome()).isEqualTo("Maria Silva");
        assertThat(usuario.getEmail()).isEqualTo("maria@example.com");
        assertThat(usuario.getSenhaHash()).isEqualTo("hash-da-senha");
        assertThat(usuario.getStatus()).isEqualTo(StatusUsuario.ATIVO);
        assertThat(usuario.getCriadoEm()).isNotNull();
        assertThat(usuario.getAtualizadoEm()).isNotNull();
    }

    /** RN-13, T1.11.4 (RNF-09/LGPD): soft-delete + anonimizacao - nome/e-mail/senha nunca mais identificam a pessoa. */
    @Test
    void excluirAnonimizaNomeEmailESenhaEMudaOStatusParaExcluido() {
        Usuario usuario = Usuario.novo("Maria Silva", "maria@example.com", "hash-da-senha");
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(usuario, "id", id);

        usuario.excluir();

        assertThat(usuario.getStatus()).isEqualTo(StatusUsuario.EXCLUIDO);
        assertThat(usuario.getNome()).isNotEqualTo("Maria Silva");
        assertThat(usuario.getEmail()).isNotEqualTo("maria@example.com").contains(id.toString());
        assertThat(usuario.getSenhaHash()).isEmpty();
    }
}
