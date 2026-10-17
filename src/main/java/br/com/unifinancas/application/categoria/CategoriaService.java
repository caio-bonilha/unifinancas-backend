package br.com.unifinancas.application.categoria;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.categoria.Categoria;
import br.com.unifinancas.domain.categoria.CategoriaRepository;
import br.com.unifinancas.domain.categoria.TipoCategoria;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-09, SAD Sec4.10: cadastro hierarquico de Categoria de um Ambiente Financeiro. */
@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    /** SAD Sec7.2: listagem em arvore - raizes com suas subcategorias aninhadas recursivamente. */
    public List<CategoriaResumo> listarArvore(TenantContext contexto) {
        List<Categoria> todas = categoriaRepository.findAllByAmbienteFinanceiroId(contexto.ambienteFinanceiroId());

        Map<UUID, List<Categoria>> filhasPorPai = todas.stream()
                .filter(categoria -> categoria.getCategoriaPaiId() != null)
                .collect(Collectors.groupingBy(Categoria::getCategoriaPaiId));

        return todas.stream()
                .filter(categoria -> categoria.getCategoriaPaiId() == null)
                .map(categoria -> paraResumo(categoria, filhasPorPai))
                .toList();
    }

    /**
     * SAD Sec8.2: escrita financeira exige OWNER, ADMIN ou MEMBRO. Se
     * {@code categoriaPaiId} for informado, precisa pertencer ao mesmo
     * Ambiente Financeiro (Sec8.7/Sec12.4) - nunca aceitar um id de outro
     * tenant como pai.
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public CategoriaResumo criar(TenantContext contexto, String nome, TipoCategoria tipo, UUID categoriaPaiId) {
        if (categoriaPaiId != null) {
            categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaPaiId, contexto.ambienteFinanceiroId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "CATEGORIA_PAI_NAO_ENCONTRADA", "Categoria pai nao encontrada neste Ambiente Financeiro."));
        }

        Categoria categoria = Categoria.novo(contexto.ambienteFinanceiroId(), nome, tipo, categoriaPaiId);
        categoriaRepository.save(categoria);
        return new CategoriaResumo(categoria.getId(), categoria.getNome(), categoria.getTipo(),
                categoria.getCategoriaPaiId(), categoria.isAtiva(), List.of());
    }

    /**
     * [Gap identificado pelo usuario]: {@code categoriaPaiId} passa a ser
     * editavel (T1.9.6 tinha deixado de fora por exigir deteccao de ciclo).
     * Validacoes antes de tocar o dominio: (1) uma categoria nao pode ser
     * pai dela mesma; (2) a categoria pai precisa existir neste mesmo
     * Ambiente Financeiro (Sec8.7/Sec12.4, mesmo criterio de {@link #criar});
     * (3) a nova categoria pai nao pode ser uma descendente da categoria
     * sendo editada - senao a hierarquia vira um ciclo.
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public CategoriaResumo editar(TenantContext contexto, UUID id, String nome, TipoCategoria tipo, UUID categoriaPaiId) {
        Categoria categoria = buscarOuFalhar(contexto, id);
        validarCategoriaPai(contexto, id, categoriaPaiId);
        categoria.editar(nome, tipo, categoriaPaiId);
        categoriaRepository.save(categoria);
        return new CategoriaResumo(categoria.getId(), categoria.getNome(), categoria.getTipo(),
                categoria.getCategoriaPaiId(), categoria.isAtiva(), List.of());
    }

    private void validarCategoriaPai(TenantContext contexto, UUID id, UUID categoriaPaiId) {
        if (categoriaPaiId == null) {
            return;
        }
        if (categoriaPaiId.equals(id)) {
            throw new RegraDeNegocioException(
                    "CATEGORIA_PAI_INVALIDA", "Uma categoria nao pode ser pai dela mesma.");
        }
        UUID ambienteId = contexto.ambienteFinanceiroId();
        UUID atual = categoriaPaiId;
        while (atual != null) {
            Categoria candidata = categoriaRepository.findByIdAndAmbienteFinanceiroId(atual, ambienteId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "CATEGORIA_PAI_NAO_ENCONTRADA", "Categoria pai nao encontrada neste Ambiente Financeiro."));
            if (candidata.getId().equals(id)) {
                throw new RegraDeNegocioException("CATEGORIA_PAI_CICLICA",
                        "A categoria pai escolhida e uma descendente desta categoria - isso criaria um ciclo na hierarquia.");
            }
            atual = candidata.getCategoriaPaiId();
        }
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public CategoriaResumo ativar(TenantContext contexto, UUID id) {
        Categoria categoria = buscarOuFalhar(contexto, id);
        categoria.ativar();
        categoriaRepository.save(categoria);
        return new CategoriaResumo(categoria.getId(), categoria.getNome(), categoria.getTipo(),
                categoria.getCategoriaPaiId(), categoria.isAtiva(), List.of());
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public CategoriaResumo desativar(TenantContext contexto, UUID id) {
        Categoria categoria = buscarOuFalhar(contexto, id);
        categoria.desativar();
        categoriaRepository.save(categoria);
        return new CategoriaResumo(categoria.getId(), categoria.getNome(), categoria.getTipo(),
                categoria.getCategoriaPaiId(), categoria.isAtiva(), List.of());
    }

    private Categoria buscarOuFalhar(TenantContext contexto, UUID id) {
        return categoriaRepository.findByIdAndAmbienteFinanceiroId(id, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CATEGORIA_NAO_ENCONTRADA", "Categoria nao encontrada neste Ambiente Financeiro."));
    }

    private CategoriaResumo paraResumo(Categoria categoria, Map<UUID, List<Categoria>> filhasPorPai) {
        List<CategoriaResumo> subcategorias = filhasPorPai.getOrDefault(categoria.getId(), List.of()).stream()
                .map(filha -> paraResumo(filha, filhasPorPai))
                .toList();
        return new CategoriaResumo(categoria.getId(), categoria.getNome(), categoria.getTipo(),
                categoria.getCategoriaPaiId(), categoria.isAtiva(), subcategorias);
    }
}
