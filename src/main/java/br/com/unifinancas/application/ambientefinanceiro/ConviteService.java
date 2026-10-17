package br.com.unifinancas.application.ambientefinanceiro;

import br.com.unifinancas.domain.ambientefinanceiro.Convite;
import br.com.unifinancas.domain.ambientefinanceiro.ConviteRepository;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.domain.usuario.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-04, SAD Sec12.5: convite de um e-mail para ingressar em um Ambiente
 * Financeiro, e aceite pelo lado de quem ja tinha conta no momento do
 * convite. O lado "e-mail sem conta" (cria o Usuario e o vinculo juntos) e
 * {@link br.com.unifinancas.application.usuario.IdentidadeService#ativarConvite}.
 */
@Service
public class ConviteService {

    private static final Duration VALIDADE_CONVITE = Duration.ofDays(7);

    private final ConviteRepository conviteRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository;

    public ConviteService(ConviteRepository conviteRepository, UsuarioRepository usuarioRepository,
            UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository) {
        this.conviteRepository = conviteRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioAmbienteFinanceiroRepository = usuarioAmbienteFinanceiroRepository;
    }

    /** SAD Sec12.3: convidar exige OWNER ou ADMIN. */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN})
    @Transactional
    public ConviteResumo convidar(TenantContext contexto, String email, PerfilUsuarioAmbiente perfil) {
        UUID usuarioId = usuarioRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
        String tokenBruto = gerarTokenBruto();

        Convite convite = Convite.novo(contexto.ambienteFinanceiroId(), email, usuarioId, perfil,
                sha256(tokenBruto), Instant.now().plus(VALIDADE_CONVITE));
        conviteRepository.save(convite);

        return new ConviteResumo(convite.getId(), convite.getEmail(), convite.getPerfil(), convite.getStatus(),
                convite.getExpiraEm(), tokenBruto);
    }

    /**
     * Aceite pelo usuario autenticado que ja tinha conta no momento do
     * convite (T1.1.8) - {@code POST /v1/ambientes/{id}/convites/aceitar},
     * isento do header X-Ambiente-Financeiro-Id (TenantContextFilter): quem
     * aceita ainda nao tem vinculo com este ambiente.
     */
    @Transactional
    public void aceitar(UUID ambienteFinanceiroId, UUID usuarioIdAutenticado, String tokenBruto) {
        Convite convite = conviteRepository.findByTokenHash(sha256(tokenBruto)).orElseThrow(this::conviteNaoEncontrado);

        if (!convite.getAmbienteFinanceiroId().equals(ambienteFinanceiroId)) {
            throw conviteNaoEncontrado();
        }
        if (convite.getUsuarioId() == null) {
            throw new RegraDeNegocioException("CONVITE_REQUER_ATIVACAO",
                    "Este convite e para um e-mail sem conta - use o link de ativacao de conta.");
        }
        if (!convite.getUsuarioId().equals(usuarioIdAutenticado)) {
            throw new RegraDeNegocioException("CONVITE_NAO_PERTENCE_AO_USUARIO",
                    "Este convite nao pertence ao usuario autenticado.");
        }

        convite.aceitar();
        conviteRepository.save(convite);

        Optional<UsuarioAmbienteFinanceiro> existente = usuarioAmbienteFinanceiroRepository
                .findByUsuarioIdAndAmbienteFinanceiroId(usuarioIdAutenticado, ambienteFinanceiroId);
        if (existente.isPresent()) {
            UsuarioAmbienteFinanceiro vinculo = existente.get();
            if (vinculo.estaAtivo()) {
                throw new RegraDeNegocioException("USUARIO_JA_MEMBRO", "Usuario ja e membro ativo deste ambiente.");
            }
            vinculo.reativar(convite.getPerfil());
            usuarioAmbienteFinanceiroRepository.save(vinculo);
        } else {
            usuarioAmbienteFinanceiroRepository.save(
                    UsuarioAmbienteFinanceiro.vincularComPerfil(usuarioIdAutenticado, ambienteFinanceiroId, convite.getPerfil()));
        }
    }

    private RecursoNaoEncontradoException conviteNaoEncontrado() {
        return new RecursoNaoEncontradoException("CONVITE_NAO_ENCONTRADO", "Convite invalido, expirado ou ja utilizado.");
    }

    private String gerarTokenBruto() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponivel na JVM.", e);
        }
    }
}
