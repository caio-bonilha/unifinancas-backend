package br.com.unifinancas.application.categoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.categoria.Categoria;
import br.com.unifinancas.domain.categoria.CategoriaRepository;
import br.com.unifinancas.domain.categoria.TipoCategoria;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CategoriaServiceTest {

    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final CategoriaService service = new CategoriaService(categoriaRepository);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);

    @Test
    void listarArvoreMontaRaizesComSubcategoriasAninhadas() {
        Categoria raiz = comId(Categoria.novo(ambienteFinanceiroId, "Alimentacao", TipoCategoria.DESPESA, null));
        Categoria filha = comId(Categoria.novo(ambienteFinanceiroId, "Restaurante", TipoCategoria.DESPESA, raiz.getId()));
        Categoria neta = comId(Categoria.novo(ambienteFinanceiroId, "Delivery", TipoCategoria.DESPESA, filha.getId()));
        when(categoriaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId))
                .thenReturn(List.of(raiz, filha, neta));

        List<CategoriaResumo> arvore = service.listarArvore(contexto);

        assertThat(arvore).hasSize(1);
        CategoriaResumo resumoRaiz = arvore.get(0);
        assertThat(resumoRaiz.nome()).isEqualTo("Alimentacao");
        assertThat(resumoRaiz.subcategorias()).hasSize(1);
        CategoriaResumo resumoFilha = resumoRaiz.subcategorias().get(0);
        assertThat(resumoFilha.nome()).isEqualTo("Restaurante");
        assertThat(resumoFilha.subcategorias()).hasSize(1);
        assertThat(resumoFilha.subcategorias().get(0).nome()).isEqualTo("Delivery");
    }

    @Test
    void semNenhumaCategoriaRetornaArvoreVazia() {
        when(categoriaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of());

        assertThat(service.listarArvore(contexto)).isEmpty();
    }

    @Test
    void criarCategoriaRaizSemPaiFunciona() {
        CategoriaResumo resumo = service.criar(contexto, "Salario", TipoCategoria.RECEITA, null);

        ArgumentCaptor<Categoria> capturada = ArgumentCaptor.forClass(Categoria.class);
        verify(categoriaRepository).save(capturada.capture());
        assertThat(capturada.getValue().getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(capturada.getValue().getCategoriaPaiId()).isNull();

        assertThat(resumo.nome()).isEqualTo("Salario");
        assertThat(resumo.subcategorias()).isEmpty();
    }

    @Test
    void criarComPaiDoMesmoAmbienteFunciona() {
        UUID paiId = UUID.randomUUID();
        Categoria pai = comId(Categoria.novo(ambienteFinanceiroId, "Alimentacao", TipoCategoria.DESPESA, null), paiId);
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(paiId, ambienteFinanceiroId)).thenReturn(Optional.of(pai));

        CategoriaResumo resumo = service.criar(contexto, "Restaurante", TipoCategoria.DESPESA, paiId);

        ArgumentCaptor<Categoria> capturada = ArgumentCaptor.forClass(Categoria.class);
        verify(categoriaRepository).save(capturada.capture());
        assertThat(capturada.getValue().getCategoriaPaiId()).isEqualTo(paiId);
        assertThat(resumo.nome()).isEqualTo("Restaurante");
    }

    @Test
    void criarComPaiInexistenteOuDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID paiId = UUID.randomUUID();
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(paiId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(contexto, "Restaurante", TipoCategoria.DESPESA, paiId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void editarAtualizaNomeETipo() {
        UUID categoriaId = UUID.randomUUID();
        Categoria categoria = comId(Categoria.novo(ambienteFinanceiroId, "Antigo", TipoCategoria.DESPESA, null), categoriaId);
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.of(categoria));

        CategoriaResumo resumo = service.editar(contexto, categoriaId, "Novo Nome", TipoCategoria.AMBOS, null);

        assertThat(resumo.nome()).isEqualTo("Novo Nome");
        assertThat(resumo.tipo()).isEqualTo(TipoCategoria.AMBOS);
        verify(categoriaRepository).save(categoria);
    }

    @Test
    void editarCategoriaInexistenteOuDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID categoriaId = UUID.randomUUID();
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editar(contexto, categoriaId, "X", TipoCategoria.DESPESA, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void editarComCategoriaPaiDoMesmoAmbienteFunciona() {
        UUID categoriaId = UUID.randomUUID();
        UUID paiId = UUID.randomUUID();
        Categoria categoria = comId(Categoria.novo(ambienteFinanceiroId, "Uber", TipoCategoria.DESPESA, null), categoriaId);
        Categoria pai = comId(Categoria.novo(ambienteFinanceiroId, "Transporte", TipoCategoria.DESPESA, null), paiId);
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.of(categoria));
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(paiId, ambienteFinanceiroId)).thenReturn(Optional.of(pai));

        CategoriaResumo resumo = service.editar(contexto, categoriaId, "Uber", TipoCategoria.DESPESA, paiId);

        assertThat(resumo.categoriaPaiId()).isEqualTo(paiId);
        assertThat(categoria.getCategoriaPaiId()).isEqualTo(paiId);
    }

    @Test
    void editarComCategoriaPaiIgualAPropriaCategoriaLancaRegraDeNegocio() {
        UUID categoriaId = UUID.randomUUID();
        Categoria categoria = comId(Categoria.novo(ambienteFinanceiroId, "Uber", TipoCategoria.DESPESA, null), categoriaId);
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.of(categoria));

        assertThatThrownBy(() -> service.editar(contexto, categoriaId, "Uber", TipoCategoria.DESPESA, categoriaId))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("CATEGORIA_PAI_INVALIDA"));
    }

    @Test
    void editarComCategoriaPaiDescendenteLancaRegraDeNegocioDeCiclo() {
        UUID raizId = UUID.randomUUID();
        UUID filhaId = UUID.randomUUID();
        UUID netaId = UUID.randomUUID();
        Categoria raiz = comId(Categoria.novo(ambienteFinanceiroId, "Transporte", TipoCategoria.DESPESA, null), raizId);
        Categoria filha = comId(Categoria.novo(ambienteFinanceiroId, "Uber", TipoCategoria.DESPESA, raizId), filhaId);
        Categoria neta = comId(Categoria.novo(ambienteFinanceiroId, "Uber Eats", TipoCategoria.DESPESA, filhaId), netaId);
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(raizId, ambienteFinanceiroId)).thenReturn(Optional.of(raiz));
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(filhaId, ambienteFinanceiroId)).thenReturn(Optional.of(filha));
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(netaId, ambienteFinanceiroId)).thenReturn(Optional.of(neta));

        // "Transporte" (raiz) nao pode virar filha de "Uber Eats" (sua propria neta) - criaria um ciclo.
        assertThatThrownBy(() -> service.editar(contexto, raizId, "Transporte", TipoCategoria.DESPESA, netaId))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("CATEGORIA_PAI_CICLICA"));
    }

    @Test
    void editarComCategoriaPaiInexistenteOuDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID categoriaId = UUID.randomUUID();
        UUID paiId = UUID.randomUUID();
        Categoria categoria = comId(Categoria.novo(ambienteFinanceiroId, "Uber", TipoCategoria.DESPESA, null), categoriaId);
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.of(categoria));
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(paiId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editar(contexto, categoriaId, "Uber", TipoCategoria.DESPESA, paiId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void editarComCategoriaPaiNulaVoltaASerRaiz() {
        UUID categoriaId = UUID.randomUUID();
        UUID paiId = UUID.randomUUID();
        Categoria categoria = comId(Categoria.novo(ambienteFinanceiroId, "Uber", TipoCategoria.DESPESA, paiId), categoriaId);
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.of(categoria));

        CategoriaResumo resumo = service.editar(contexto, categoriaId, "Uber", TipoCategoria.DESPESA, null);

        assertThat(resumo.categoriaPaiId()).isNull();
        assertThat(categoria.getCategoriaPaiId()).isNull();
    }

    @Test
    void desativarEAtivarAlternamOFlag() {
        UUID categoriaId = UUID.randomUUID();
        Categoria categoria = comId(Categoria.novo(ambienteFinanceiroId, "Categoria", TipoCategoria.DESPESA, null), categoriaId);
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.of(categoria));

        CategoriaResumo desativada = service.desativar(contexto, categoriaId);
        assertThat(desativada.ativa()).isFalse();

        CategoriaResumo reativada = service.ativar(contexto, categoriaId);
        assertThat(reativada.ativa()).isTrue();
    }

    private Categoria comId(Categoria categoria) {
        return comId(categoria, UUID.randomUUID());
    }

    private Categoria comId(Categoria categoria, UUID id) {
        ReflectionTestUtils.setField(categoria, "id", id);
        return categoria;
    }
}
