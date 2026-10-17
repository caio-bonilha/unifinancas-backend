package br.com.unifinancas.application.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiroRepository;
import br.com.unifinancas.domain.ambientefinanceiro.Convite;
import br.com.unifinancas.domain.ambientefinanceiro.ConviteRepository;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.TipoAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import br.com.unifinancas.domain.shared.RecursoConflitanteException;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.usuario.RedefinicaoSenha;
import br.com.unifinancas.domain.usuario.RedefinicaoSenhaRepository;
import br.com.unifinancas.domain.usuario.StatusUsuario;
import br.com.unifinancas.domain.usuario.Usuario;
import br.com.unifinancas.domain.usuario.UsuarioRepository;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class IdentidadeServiceTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final AmbienteFinanceiroRepository ambienteFinanceiroRepository = mock(AmbienteFinanceiroRepository.class);
    private final UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository =
            mock(UsuarioAmbienteFinanceiroRepository.class);
    private final ConviteRepository conviteRepository = mock(ConviteRepository.class);
    private final RedefinicaoSenhaRepository redefinicaoSenhaRepository = mock(RedefinicaoSenhaRepository.class);
    private final RedefinicaoSenhaMailerPort redefinicaoSenhaMailerPort = mock(RedefinicaoSenhaMailerPort.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final EmissorTokenPort emissorTokenPort = mock(EmissorTokenPort.class);

    private IdentidadeService service;

    @BeforeEach
    void montar() {
        service = new IdentidadeService(usuarioRepository, ambienteFinanceiroRepository,
                usuarioAmbienteFinanceiroRepository, conviteRepository, redefinicaoSenhaRepository,
                redefinicaoSenhaMailerPort, passwordEncoder, emissorTokenPort, Duration.ofHours(1),
                "http://localhost:5173");
        when(usuarioRepository.findByEmail(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha12345")).thenReturn("hash-da-senha");
        when(emissorTokenPort.emitirParDeTokens(any()))
                .thenReturn(new TokensEmitidos("access", "refresh", UUID.randomUUID()));
        when(redefinicaoSenhaRepository.findByUsuarioIdAndUsadoEmIsNull(any())).thenReturn(List.of());
    }

    private static String sha256(String valor) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void registrarCriaUsuarioAmbientePessoalEVinculoOwnerEmUmaTransacao() {
        ResultadoCadastro resultado = service.registrar("Maria Silva", "maria@example.com", "senha12345");

        var usuarioCapturado = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(usuarioCapturado.capture());
        assertThat(usuarioCapturado.getValue().getEmail()).isEqualTo("maria@example.com");
        assertThat(usuarioCapturado.getValue().getSenhaHash()).isEqualTo("hash-da-senha");

        var ambienteCapturado = org.mockito.ArgumentCaptor.forClass(br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiro.class);
        verify(ambienteFinanceiroRepository).save(ambienteCapturado.capture());
        assertThat(ambienteCapturado.getValue().getTipo()).isEqualTo(TipoAmbienteFinanceiro.PESSOAL);
        assertThat(ambienteCapturado.getValue().getNome()).contains("Maria Silva");

        var vinculoCapturado = org.mockito.ArgumentCaptor.forClass(UsuarioAmbienteFinanceiro.class);
        verify(usuarioAmbienteFinanceiroRepository).save(vinculoCapturado.capture());
        assertThat(vinculoCapturado.getValue().getPerfil())
                .isEqualTo(br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente.OWNER);
        assertThat(vinculoCapturado.getValue().estaAtivo()).isTrue();

        assertThat(resultado.accessToken()).isEqualTo("access");
        assertThat(resultado.refreshToken()).isEqualTo("refresh");
        assertThat(resultado.ambienteFinanceiroId()).isEqualTo(ambienteCapturado.getValue().getId());
        assertThat(resultado.nome()).isEqualTo("Maria Silva");
    }

    @Test
    void emailJaCadastradoLancaRecursoConflitanteENaoCriaMaisNada() {
        when(usuarioRepository.findByEmail("ja-existe@example.com"))
                .thenReturn(Optional.of(Usuario.novo("Outro", "ja-existe@example.com", "hash")));

        assertThatThrownBy(() -> service.registrar("Novo", "ja-existe@example.com", "senha12345"))
                .isInstanceOf(RecursoConflitanteException.class)
                .satisfies(e -> assertThat(((RecursoConflitanteException) e).getCodigo()).isEqualTo("EMAIL_JA_CADASTRADO"));

        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(ambienteFinanceiroRepository, never()).save(any());
        verify(usuarioAmbienteFinanceiroRepository, never()).save(any());
    }

    @Test
    void corridaDeEmailDuplicadoNaConstraintDoBancoTambemViraRecursoConflitante() {
        doThrow(new DataIntegrityViolationException("uq_usuario_email"))
                .when(usuarioRepository).saveAndFlush(any());

        assertThatThrownBy(() -> service.registrar("Novo", "corrida@example.com", "senha12345"))
                .isInstanceOf(RecursoConflitanteException.class);

        verify(ambienteFinanceiroRepository, never()).save(any());
    }

    @Test
    void autenticarComCredenciaisValidasRetornaTokens() {
        Usuario usuario = Usuario.novo("Maria", "maria.login@example.com", "hash-armazenado");
        when(usuarioRepository.findByEmail("maria.login@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha-correta", "hash-armazenado")).thenReturn(true);

        ResultadoLogin resultado = service.autenticar("maria.login@example.com", "senha-correta");

        assertThat(resultado.accessToken()).isEqualTo("access");
        assertThat(resultado.refreshToken()).isEqualTo("refresh");
        assertThat(resultado.nome()).isEqualTo("Maria");
    }

    @Test
    void autenticarComEmailInexistenteLancaBadCredentials() {
        when(usuarioRepository.findByEmail("naoexiste@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.autenticar("naoexiste@example.com", "qualquer"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void autenticarComSenhaErradaLancaBadCredentials() {
        Usuario usuario = Usuario.novo("Maria", "maria.senhaerrada@example.com", "hash-armazenado");
        when(usuarioRepository.findByEmail("maria.senhaerrada@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha-errada", "hash-armazenado")).thenReturn(false);

        assertThatThrownBy(() -> service.autenticar("maria.senhaerrada@example.com", "senha-errada"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void autenticarComUsuarioBloqueadoLancaBadCredentials() {
        Usuario usuario = Usuario.novo("Maria", "maria.bloqueada@example.com", "hash-armazenado");
        ReflectionTestUtils.setField(usuario, "status", StatusUsuario.BLOQUEADO);
        when(usuarioRepository.findByEmail("maria.bloqueada@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha-correta", "hash-armazenado")).thenReturn(true);

        assertThatThrownBy(() -> service.autenticar("maria.bloqueada@example.com", "senha-correta"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void renovarDelegaParaOPortEDevolveNovosTokens() {
        UUID usuarioId = UUID.randomUUID();
        Usuario usuario = Usuario.novo("Joana", "joana.renovar@example.com", "hash");
        when(emissorTokenPort.rotacionar("token-antigo"))
                .thenReturn(new TokensEmitidos("novo-access", "novo-refresh", usuarioId));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        ResultadoLogin resultado = service.renovar("token-antigo");

        assertThat(resultado.accessToken()).isEqualTo("novo-access");
        assertThat(resultado.refreshToken()).isEqualTo("novo-refresh");
        assertThat(resultado.nome()).isEqualTo("Joana");
    }

    @Test
    void renovarComUsuarioInexistenteLancaRecursoNaoEncontrado() {
        UUID usuarioId = UUID.randomUUID();
        when(emissorTokenPort.rotacionar("token-orfao"))
                .thenReturn(new TokensEmitidos("novo-access", "novo-refresh", usuarioId));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renovar("token-orfao")).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void ativarConviteCriaUsuarioEVinculoAtivoParaEmailSemConta() {
        UUID ambienteFinanceiroId = UUID.randomUUID();
        Convite convite = Convite.novo(ambienteFinanceiroId, "novo@example.com", null, PerfilUsuarioAmbiente.MEMBRO,
                sha256("token-bruto"), Instant.now().plus(java.time.Duration.ofDays(7)));
        when(conviteRepository.findByTokenHash(sha256("token-bruto"))).thenReturn(Optional.of(convite));
        when(passwordEncoder.encode("senha12345")).thenReturn("hash-da-senha");

        ResultadoCadastro resultado = service.ativarConvite("token-bruto", "Novo Usuario", "senha12345");

        var usuarioCapturado = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(usuarioCapturado.capture());
        assertThat(usuarioCapturado.getValue().getEmail()).isEqualTo("novo@example.com");

        var vinculoCapturado = org.mockito.ArgumentCaptor.forClass(UsuarioAmbienteFinanceiro.class);
        verify(usuarioAmbienteFinanceiroRepository).save(vinculoCapturado.capture());
        assertThat(vinculoCapturado.getValue().getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(vinculoCapturado.getValue().getPerfil()).isEqualTo(PerfilUsuarioAmbiente.MEMBRO);
        assertThat(vinculoCapturado.getValue().estaAtivo()).isTrue();

        assertThat(convite.getStatus()).isEqualTo(br.com.unifinancas.domain.ambientefinanceiro.StatusConvite.ACEITO);
        assertThat(resultado.ambienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(resultado.accessToken()).isEqualTo("access");
        assertThat(resultado.nome()).isEqualTo("Novo Usuario");
    }

    @Test
    void ativarConviteComTokenInexistenteLancaRecursoNaoEncontrado() {
        when(conviteRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ativarConvite("token-invalido", "Nome", "senha12345"))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void ativarConviteComEmailJaTendoContaLancaRecursoConflitante() {
        UUID ambienteFinanceiroId = UUID.randomUUID();
        Convite convite = Convite.novo(ambienteFinanceiroId, "ja-tem-conta@example.com", null,
                PerfilUsuarioAmbiente.MEMBRO, sha256("token-bruto"), Instant.now().plus(java.time.Duration.ofDays(7)));
        when(conviteRepository.findByTokenHash(sha256("token-bruto"))).thenReturn(Optional.of(convite));
        when(usuarioRepository.findByEmail("ja-tem-conta@example.com"))
                .thenReturn(Optional.of(Usuario.novo("Outro", "ja-tem-conta@example.com", "hash")));

        assertThatThrownBy(() -> service.ativarConvite("token-bruto", "Nome", "senha12345"))
                .isInstanceOf(RecursoConflitanteException.class);

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void ativarConviteComConviteJaAceitoLancaRecursoNaoEncontrado() {
        UUID ambienteFinanceiroId = UUID.randomUUID();
        Convite convite = Convite.novo(ambienteFinanceiroId, "novo@example.com", null, PerfilUsuarioAmbiente.MEMBRO,
                sha256("token-bruto"), Instant.now().plus(java.time.Duration.ofDays(7)));
        convite.aceitar();
        when(conviteRepository.findByTokenHash(sha256("token-bruto"))).thenReturn(Optional.of(convite));

        assertThatThrownBy(() -> service.ativarConvite("token-bruto", "Nome", "senha12345"))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    // --- T1.1.10: recuperacao de senha (Sec6.7) ------------------------------

    @Test
    void solicitarRedefinicaoSenhaComEmailAtivoGravaTokenEEnviaLink() {
        Usuario usuario = usuarioComId("maria.reset@example.com", StatusUsuario.ATIVO);
        when(usuarioRepository.findByEmail("maria.reset@example.com")).thenReturn(Optional.of(usuario));

        service.solicitarRedefinicaoSenha("maria.reset@example.com");

        var registroCapturado = org.mockito.ArgumentCaptor.forClass(RedefinicaoSenha.class);
        verify(redefinicaoSenhaRepository).save(registroCapturado.capture());
        assertThat(registroCapturado.getValue().getUsuarioId()).isEqualTo(usuario.getId());
        assertThat(registroCapturado.getValue().estaValido()).isTrue();
        assertThat(registroCapturado.getValue().getTokenHash()).hasSize(64);

        var linkCapturado = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redefinicaoSenhaMailerPort)
                .enviarLinkRedefinicao(org.mockito.ArgumentMatchers.eq("maria.reset@example.com"),
                        org.mockito.ArgumentMatchers.eq("Maria"), linkCapturado.capture());
        assertThat(linkCapturado.getValue()).startsWith("http://localhost:5173/redefinir-senha?token=");
    }

    @Test
    void solicitarRedefinicaoSenhaComEmailInexistenteNaoFazNadaENaoLanca() {
        when(usuarioRepository.findByEmail("naoexiste@example.com")).thenReturn(Optional.empty());

        service.solicitarRedefinicaoSenha("naoexiste@example.com");

        verify(redefinicaoSenhaRepository, never()).save(any());
        verify(redefinicaoSenhaMailerPort, never()).enviarLinkRedefinicao(any(), any(), any());
    }

    @Test
    void solicitarRedefinicaoSenhaComUsuarioBloqueadoNaoGeraTokenNemEmail() {
        Usuario usuario = usuarioComId("bloqueado@example.com", StatusUsuario.BLOQUEADO);
        when(usuarioRepository.findByEmail("bloqueado@example.com")).thenReturn(Optional.of(usuario));

        service.solicitarRedefinicaoSenha("bloqueado@example.com");

        verify(redefinicaoSenhaRepository, never()).save(any());
        verify(redefinicaoSenhaMailerPort, never()).enviarLinkRedefinicao(any(), any(), any());
    }

    @Test
    void solicitarRedefinicaoSenhaEngoleFalhaDoEnvioDeEmail() {
        Usuario usuario = usuarioComId("falha.email@example.com", StatusUsuario.ATIVO);
        when(usuarioRepository.findByEmail("falha.email@example.com")).thenReturn(Optional.of(usuario));
        doThrow(new RuntimeException("SMTP fora do ar"))
                .when(redefinicaoSenhaMailerPort).enviarLinkRedefinicao(any(), any(), any());

        service.solicitarRedefinicaoSenha("falha.email@example.com");

        verify(redefinicaoSenhaRepository).save(any());
    }

    @Test
    void redefinirSenhaComTokenValidoTrocaHashInvalidaPendentesERevogaSessoes() {
        Usuario usuario = usuarioComId("troca@example.com", StatusUsuario.ATIVO);
        RedefinicaoSenha registro = RedefinicaoSenha.nova(usuario.getId(), sha256("tok"), Instant.now().plusSeconds(600));
        RedefinicaoSenha outroPendente =
                RedefinicaoSenha.nova(usuario.getId(), sha256("tok-antigo"), Instant.now().plusSeconds(600));
        when(redefinicaoSenhaRepository.findByTokenHash(sha256("tok"))).thenReturn(Optional.of(registro));
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(redefinicaoSenhaRepository.findByUsuarioIdAndUsadoEmIsNull(usuario.getId()))
                .thenReturn(List.of(registro, outroPendente));
        when(passwordEncoder.encode("nova-senha-123")).thenReturn("novo-hash");

        service.redefinirSenha("tok", "nova-senha-123");

        assertThat(usuario.getSenhaHash()).isEqualTo("novo-hash");
        assertThat(registro.getUsadoEm()).isNotNull();
        assertThat(outroPendente.getUsadoEm()).isNotNull();
        verify(emissorTokenPort).revogarTodosDoUsuario(usuario.getId());
    }

    @Test
    void redefinirSenhaComTokenInexistenteLanca422() {
        when(redefinicaoSenhaRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redefinirSenha("nao-existe", "nova-senha-123"))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo())
                        .isEqualTo("TOKEN_REDEFINICAO_INVALIDO"));
        verify(emissorTokenPort, never()).revogarTodosDoUsuario(any());
    }

    @Test
    void redefinirSenhaComTokenExpiradoLanca422() {
        RedefinicaoSenha expirado =
                RedefinicaoSenha.nova(UUID.randomUUID(), sha256("exp"), Instant.now().minusSeconds(1));
        when(redefinicaoSenhaRepository.findByTokenHash(sha256("exp"))).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> service.redefinirSenha("exp", "nova-senha-123"))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void redefinirSenhaComTokenJaUsadoLanca422() {
        RedefinicaoSenha usado = RedefinicaoSenha.nova(UUID.randomUUID(), sha256("us"), Instant.now().plusSeconds(600));
        usado.marcarUsado();
        when(redefinicaoSenhaRepository.findByTokenHash(sha256("us"))).thenReturn(Optional.of(usado));

        assertThatThrownBy(() -> service.redefinirSenha("us", "nova-senha-123"))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    // --- T1.1.10: alteracao de senha autenticado (Sec6.8) ------------------

    @Test
    void alterarSenhaComSenhaAtualCorretaTrocaHashERevogaSessoes() {
        Usuario usuario = usuarioComId("logado@example.com", StatusUsuario.ATIVO);
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("atual-ok", usuario.getSenhaHash())).thenReturn(true);
        when(passwordEncoder.encode("nova-senha-123")).thenReturn("novo-hash");

        service.alterarSenha(usuario.getId(), "atual-ok", "nova-senha-123");

        assertThat(usuario.getSenhaHash()).isEqualTo("novo-hash");
        verify(emissorTokenPort).revogarTodosDoUsuario(usuario.getId());
    }

    @Test
    void alterarSenhaComSenhaAtualIncorretaLanca422SemTrocarNemRevogar() {
        Usuario usuario = usuarioComId("logado2@example.com", StatusUsuario.ATIVO);
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("atual-errada", usuario.getSenhaHash())).thenReturn(false);

        assertThatThrownBy(() -> service.alterarSenha(usuario.getId(), "atual-errada", "nova-senha-123"))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("SENHA_ATUAL_INCORRETA"));

        verify(usuarioRepository, never()).save(any());
        verify(emissorTokenPort, never()).revogarTodosDoUsuario(any());
    }

    @Test
    void alterarSenhaComUsuarioInexistenteLancaRecursoNaoEncontrado() {
        UUID usuarioId = UUID.randomUUID();
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.alterarSenha(usuarioId, "qualquer", "nova-senha-123"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private static Usuario usuarioComId(String email, StatusUsuario status) {
        Usuario usuario = Usuario.novo("Maria", email, "hash-atual");
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        if (status != StatusUsuario.ATIVO) {
            ReflectionTestUtils.setField(usuario, "status", status);
        }
        return usuario;
    }
}
