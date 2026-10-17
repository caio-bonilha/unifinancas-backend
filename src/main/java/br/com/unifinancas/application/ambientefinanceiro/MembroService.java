package br.com.unifinancas.application.ambientefinanceiro;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.StatusVinculo;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.domain.usuario.Usuario;
import br.com.unifinancas.domain.usuario.UsuarioRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SAD Sec12.3: gestao de membros de um Ambiente Financeiro (RF-04). */
@Service
public class MembroService {

    private final UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository;
    private final UsuarioRepository usuarioRepository;

    public MembroService(UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository,
            UsuarioRepository usuarioRepository) {
        this.usuarioAmbienteFinanceiroRepository = usuarioAmbienteFinanceiroRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /** Listagem e permitida a qualquer membro ativo - a leitura de dados nao e restrita a OWNER/ADMIN (Sec12.3). */
    public List<MembroResumo> listar(TenantContext contexto) {
        return usuarioAmbienteFinanceiroRepository
                .findAllByAmbienteFinanceiroIdAndStatus(contexto.ambienteFinanceiroId(), StatusVinculo.ATIVO)
                .stream()
                .map(this::paraResumo)
                .toList();
    }

    /**
     * SAD Sec12.3: ADMIN pode alterar perfil de outro membro, exceto
     * rebaixar/remover o OWNER; e um ambiente sempre precisa ter ao menos
     * um OWNER ativo (bloqueia rebaixar o ultimo).
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN})
    @Transactional
    public void alterarPerfil(TenantContext contexto, UUID usuarioIdAlvo, PerfilUsuarioAmbiente novoPerfil) {
        UsuarioAmbienteFinanceiro vinculo = localizarVinculoAtivo(contexto, usuarioIdAlvo);

        if (contexto.perfil() == PerfilUsuarioAmbiente.ADMIN && vinculo.getPerfil() == PerfilUsuarioAmbiente.OWNER) {
            throw adminNaoPodeAlterarOwner();
        }
        if (vinculo.getPerfil() == PerfilUsuarioAmbiente.OWNER && novoPerfil != PerfilUsuarioAmbiente.OWNER) {
            garantirQueNaoEUltimoOwnerAtivo(contexto.ambienteFinanceiroId());
        }

        vinculo.alterarPerfil(novoPerfil);
        usuarioAmbienteFinanceiroRepository.save(vinculo);
    }

    /** SAD Sec12.3: mesma restricao de alterarPerfil - ADMIN nunca remove OWNER, e o ultimo OWNER nunca sai. */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN})
    @Transactional
    public void remover(TenantContext contexto, UUID usuarioIdAlvo) {
        UsuarioAmbienteFinanceiro vinculo = localizarVinculoAtivo(contexto, usuarioIdAlvo);

        if (contexto.perfil() == PerfilUsuarioAmbiente.ADMIN && vinculo.getPerfil() == PerfilUsuarioAmbiente.OWNER) {
            throw adminNaoPodeAlterarOwner();
        }
        if (vinculo.getPerfil() == PerfilUsuarioAmbiente.OWNER) {
            garantirQueNaoEUltimoOwnerAtivo(contexto.ambienteFinanceiroId());
        }

        vinculo.remover();
        usuarioAmbienteFinanceiroRepository.save(vinculo);
    }

    private void garantirQueNaoEUltimoOwnerAtivo(UUID ambienteFinanceiroId) {
        long ownersAtivos = usuarioAmbienteFinanceiroRepository.countByAmbienteFinanceiroIdAndPerfilAndStatus(
                ambienteFinanceiroId, PerfilUsuarioAmbiente.OWNER, StatusVinculo.ATIVO);
        if (ownersAtivos <= 1) {
            throw new RegraDeNegocioException("AMBIENTE_PRECISA_DE_UM_OWNER",
                    "Um Ambiente Financeiro precisa ter ao menos um OWNER ativo.");
        }
    }

    private RegraDeNegocioException adminNaoPodeAlterarOwner() {
        return new RegraDeNegocioException("ADMIN_NAO_PODE_ALTERAR_OWNER",
                "ADMIN nao pode rebaixar ou remover um OWNER.");
    }

    private UsuarioAmbienteFinanceiro localizarVinculoAtivo(TenantContext contexto, UUID usuarioIdAlvo) {
        return usuarioAmbienteFinanceiroRepository
                .findByUsuarioIdAndAmbienteFinanceiroId(usuarioIdAlvo, contexto.ambienteFinanceiroId())
                .filter(UsuarioAmbienteFinanceiro::estaAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("MEMBRO_NAO_ENCONTRADO",
                        "Membro nao encontrado neste Ambiente Financeiro."));
    }

    private MembroResumo paraResumo(UsuarioAmbienteFinanceiro vinculo) {
        Usuario usuario = usuarioRepository.findById(vinculo.getUsuarioId())
                .orElseThrow(() -> new IllegalStateException(
                        "Vinculo aponta para um Usuario inexistente: " + vinculo.getUsuarioId()));
        return new MembroResumo(usuario.getId(), usuario.getNome(), usuario.getEmail(), vinculo.getPerfil(),
                vinculo.getStatus());
    }
}
