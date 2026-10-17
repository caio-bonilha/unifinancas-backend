package br.com.unifinancas.application.usuario;

import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiroRepository;
import br.com.unifinancas.domain.ambientefinanceiro.Convite;
import br.com.unifinancas.domain.ambientefinanceiro.ConviteRepository;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fluxo 6.1 do SAD (RF-01, RF-02, RN-03): cadastro cria Usuario, o primeiro
 * Ambiente Financeiro (tipo PESSOAL) e o vinculo OWNER/ATIVO em uma unica
 * transacao. Tambem cobre login e renovacao de token (RF-01, Sec7.2) - o
 * mesmo componente do diagrama de Fluxo 6.1.
 */
@Service
public class IdentidadeService {

    private static final Logger log = LoggerFactory.getLogger(IdentidadeService.class);

    private final UsuarioRepository usuarioRepository;
    private final AmbienteFinanceiroRepository ambienteFinanceiroRepository;
    private final UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository;
    private final ConviteRepository conviteRepository;
    private final RedefinicaoSenhaRepository redefinicaoSenhaRepository;
    private final RedefinicaoSenhaMailerPort redefinicaoSenhaMailerPort;
    private final PasswordEncoder passwordEncoder;
    private final EmissorTokenPort emissorTokenPort;
    private final Duration validadeTokenRedefinicao;
    private final String urlBaseApp;

    public IdentidadeService(UsuarioRepository usuarioRepository,
            AmbienteFinanceiroRepository ambienteFinanceiroRepository,
            UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository,
            ConviteRepository conviteRepository, RedefinicaoSenhaRepository redefinicaoSenhaRepository,
            RedefinicaoSenhaMailerPort redefinicaoSenhaMailerPort, PasswordEncoder passwordEncoder,
            EmissorTokenPort emissorTokenPort,
            @Value("${unifinancas.auth.redefinicao-senha.validade:1h}") Duration validadeTokenRedefinicao,
            @Value("${unifinancas.app.url-base:http://localhost:5173}") String urlBaseApp) {
        this.usuarioRepository = usuarioRepository;
        this.ambienteFinanceiroRepository = ambienteFinanceiroRepository;
        this.usuarioAmbienteFinanceiroRepository = usuarioAmbienteFinanceiroRepository;
        this.conviteRepository = conviteRepository;
        this.redefinicaoSenhaRepository = redefinicaoSenhaRepository;
        this.redefinicaoSenhaMailerPort = redefinicaoSenhaMailerPort;
        this.passwordEncoder = passwordEncoder;
        this.emissorTokenPort = emissorTokenPort;
        this.validadeTokenRedefinicao = validadeTokenRedefinicao;
        this.urlBaseApp = urlBaseApp;
    }

    @Transactional
    public ResultadoCadastro registrar(String nome, String email, String senha) {
        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw emailJaCadastrado();
        }

        Usuario usuario = Usuario.novo(nome, email, passwordEncoder.encode(senha));
        try {
            usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            // corrida entre o check acima e o insert - a constraint unica do banco e quem decide de fato
            throw emailJaCadastrado();
        }

        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo(nome + " - Pessoal", TipoAmbienteFinanceiro.PESSOAL);
        ambienteFinanceiroRepository.save(ambiente);

        UsuarioAmbienteFinanceiro vinculo =
                UsuarioAmbienteFinanceiro.primeiroVinculo(usuario.getId(), ambiente.getId());
        usuarioAmbienteFinanceiroRepository.save(vinculo);

        TokensEmitidos tokens = emissorTokenPort.emitirParDeTokens(usuario.getId());

        return new ResultadoCadastro(tokens.accessToken(), tokens.refreshToken(), ambiente.getId(), usuario.getNome());
    }

    /**
     * RF-01. Mensagem generica tanto para e-mail inexistente quanto para senha
     * errada quanto para usuario nao ATIVO - nao da pra um invasor distinguir
     * "e-mail nao existe" de "senha errada" ou "conta bloqueada" (evita
     * enumeracao de contas).
     */
    public ResultadoLogin autenticar(String email, String senha) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .filter(u -> u.getStatus() == StatusUsuario.ATIVO)
                .filter(u -> passwordEncoder.matches(senha, u.getSenhaHash()))
                .orElseThrow(() -> new BadCredentialsException("E-mail ou senha inválidos."));

        TokensEmitidos tokens = emissorTokenPort.emitirParDeTokens(usuario.getId());
        return new ResultadoLogin(tokens.accessToken(), tokens.refreshToken(), usuario.getNome());
    }

    /** RF-01/ADR-010: valida e rotaciona o refresh token. */
    public ResultadoLogin renovar(String refreshToken) {
        TokensEmitidos tokens = emissorTokenPort.rotacionar(refreshToken);
        Usuario usuario = usuarioRepository.findById(tokens.usuarioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("USUARIO_NAO_ENCONTRADO", "Usuário nao encontrado."));
        return new ResultadoLogin(tokens.accessToken(), tokens.refreshToken(), usuario.getNome());
    }

    /**
     * RF-04, SAD Sec12.5 (segundo fluxo de convite): ativa um convite cujo
     * e-mail ainda nao tinha Usuario - cria o Usuario e o vinculo ATIVO no
     * Ambiente Financeiro do convite em uma unica transacao, e ja retorna
     * tokens (mesma forma de {@link #registrar}). Publico
     * ({@code POST /v1/auth/ativar-convite}) - o token do convite, nao uma
     * sessao, e quem autoriza a acao.
     */
    @Transactional
    public ResultadoCadastro ativarConvite(String tokenBruto, String nome, String senha) {
        Convite convite = conviteRepository.findByTokenHash(sha256(tokenBruto)).orElseThrow(this::conviteInvalido);
        if (!convite.estaPendente()) {
            throw conviteInvalido();
        }
        if (convite.getUsuarioId() != null || usuarioRepository.findByEmail(convite.getEmail()).isPresent()) {
            throw emailJaCadastrado();
        }

        Usuario usuario = Usuario.novo(nome, convite.getEmail(), passwordEncoder.encode(senha));
        try {
            usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            throw emailJaCadastrado();
        }

        convite.aceitar();
        conviteRepository.save(convite);

        UsuarioAmbienteFinanceiro vinculo = UsuarioAmbienteFinanceiro.vincularComPerfil(
                usuario.getId(), convite.getAmbienteFinanceiroId(), convite.getPerfil());
        usuarioAmbienteFinanceiroRepository.save(vinculo);

        TokensEmitidos tokens = emissorTokenPort.emitirParDeTokens(usuario.getId());
        return new ResultadoCadastro(
                tokens.accessToken(), tokens.refreshToken(), convite.getAmbienteFinanceiroId(), usuario.getNome());
    }

    /**
     * SAD Sec6.7 / RN-14, T1.1.10 ("esqueci minha senha", passo 1). Sempre
     * retorna sem erro, tenha ou nao o e-mail uma conta ATIVA - quem chama
     * ({@code AuthController}) responde 202 em qualquer caso, para nao
     * revelar a um invasor se o e-mail esta cadastrado (mesma politica
     * anti-enumeracao de {@link #autenticar}). So um Usuario ATIVO gera token
     * e recebe e-mail. Uma falha no envio do e-mail e logada e engolida -
     * propagar viraria 500 e quebraria o anti-enumeracao pelo lado do erro.
     */
    @Transactional
    public void solicitarRedefinicaoSenha(String email) {
        usuarioRepository.findByEmail(email)
                .filter(usuario -> usuario.getStatus() == StatusUsuario.ATIVO)
                .ifPresent(usuario -> {
                    String tokenBruto = gerarTokenBruto();
                    RedefinicaoSenha registro = RedefinicaoSenha.nova(usuario.getId(), sha256(tokenBruto),
                            Instant.now().plus(validadeTokenRedefinicao));
                    redefinicaoSenhaRepository.save(registro);

                    String link = urlBaseApp + "/redefinir-senha?token=" + tokenBruto;
                    try {
                        redefinicaoSenhaMailerPort.enviarLinkRedefinicao(usuario.getEmail(), usuario.getNome(), link);
                    } catch (RuntimeException e) {
                        log.warn("Falha ao enviar o e-mail de redefinicao de senha para o usuario {} - "
                                + "o token foi gravado, o usuario pode solicitar de novo.", usuario.getId(), e);
                    }
                });
    }

    /**
     * SAD Sec6.7 / RN-14, T1.1.10 ("esqueci minha senha", passo 2). Consome
     * o token (uso unico), troca o hash da senha, invalida os demais tokens
     * de redefinicao pendentes do Usuario e revoga TODOS os refresh tokens
     * dele (as sessoes em outros dispositivos caem). Nao emite tokens novos -
     * o fluxo e publico e o cliente vai para a tela de login. A politica de
     * senha (tamanho minimo) ja foi validada no DTO da requisicao.
     */
    @Transactional
    public void redefinirSenha(String tokenBruto, String novaSenha) {
        RedefinicaoSenha registro = redefinicaoSenhaRepository.findByTokenHash(sha256(tokenBruto))
                .filter(RedefinicaoSenha::estaValido)
                .orElseThrow(IdentidadeService::tokenRedefinicaoInvalido);

        Usuario usuario = usuarioRepository.findById(registro.getUsuarioId())
                .filter(u -> u.getStatus() == StatusUsuario.ATIVO)
                .orElseThrow(IdentidadeService::tokenRedefinicaoInvalido);

        usuario.alterarSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);

        List<RedefinicaoSenha> pendentes =
                redefinicaoSenhaRepository.findByUsuarioIdAndUsadoEmIsNull(registro.getUsuarioId());
        pendentes.forEach(RedefinicaoSenha::marcarUsado);
        registro.marcarUsado();
        redefinicaoSenhaRepository.saveAll(pendentes);
        redefinicaoSenhaRepository.save(registro);

        emissorTokenPort.revogarTodosDoUsuario(usuario.getId());
    }

    /**
     * SAD Sec6.8 / RN-14, T1.1.10: alteracao de senha pelo usuario
     * autenticado. Exige a senha atual correta e revoga todos os refresh
     * tokens do Usuario (as demais sessoes caem; o access token corrente
     * segue valido ate expirar). A politica da nova senha ja foi validada no
     * DTO da requisicao.
     */
    @Transactional
    public void alterarSenha(UUID usuarioId, String senhaAtual, String novaSenha) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .filter(u -> u.getStatus() == StatusUsuario.ATIVO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("USUARIO_NAO_ENCONTRADO", "Usuário nao encontrado."));

        if (!passwordEncoder.matches(senhaAtual, usuario.getSenhaHash())) {
            throw new RegraDeNegocioException("SENHA_ATUAL_INCORRETA", "A senha atual informada esta incorreta.");
        }

        usuario.alterarSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);

        emissorTokenPort.revogarTodosDoUsuario(usuarioId);
    }

    private static RegraDeNegocioException tokenRedefinicaoInvalido() {
        return new RegraDeNegocioException("TOKEN_REDEFINICAO_INVALIDO",
                "Link de redefinicao invalido, expirado ou ja utilizado.");
    }

    private String gerarTokenBruto() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private RecursoConflitanteException emailJaCadastrado() {
        return new RecursoConflitanteException("EMAIL_JA_CADASTRADO", "Este e-mail ja esta cadastrado.");
    }

    private RecursoNaoEncontradoException conviteInvalido() {
        return new RecursoNaoEncontradoException("CONVITE_NAO_ENCONTRADO", "Convite inválido, expirado ou ja utilizado.");
    }

    private String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível na JVM.", e);
        }
    }
}
