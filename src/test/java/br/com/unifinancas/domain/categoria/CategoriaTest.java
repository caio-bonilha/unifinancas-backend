package br.com.unifinancas.domain.categoria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CategoriaTest {

    private final UUID ambienteFinanceiroId = UUID.randomUUID();

    @Test
    void novaCategoriaRaizNasceAtivaSemPai() {
        Categoria categoria = Categoria.novo(ambienteFinanceiroId, "Alimentacao", TipoCategoria.DESPESA, null);

        assertThat(categoria.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(categoria.getNome()).isEqualTo("Alimentacao");
        assertThat(categoria.getTipo()).isEqualTo(TipoCategoria.DESPESA);
        assertThat(categoria.getCategoriaPaiId()).isNull();
        assertThat(categoria.isAtiva()).isTrue();
    }

    @Test
    void novaSubcategoriaReferenciaOPai() {
        UUID paiId = UUID.randomUUID();

        Categoria categoria = Categoria.novo(ambienteFinanceiroId, "Restaurante", TipoCategoria.DESPESA, paiId);

        assertThat(categoria.getCategoriaPaiId()).isEqualTo(paiId);
    }

    @Test
    void editarAtualizaNomeTipoECategoriaPai() {
        UUID novoPaiId = UUID.randomUUID();
        Categoria categoria = Categoria.novo(ambienteFinanceiroId, "Uber", TipoCategoria.DESPESA, null);

        categoria.editar("Uber", TipoCategoria.AMBOS, novoPaiId);

        assertThat(categoria.getNome()).isEqualTo("Uber");
        assertThat(categoria.getTipo()).isEqualTo(TipoCategoria.AMBOS);
        assertThat(categoria.getCategoriaPaiId()).isEqualTo(novoPaiId);
    }

    @Test
    void editarComCategoriaPaiNulaVoltaASerRaiz() {
        UUID paiId = UUID.randomUUID();
        Categoria categoria = Categoria.novo(ambienteFinanceiroId, "Uber", TipoCategoria.DESPESA, paiId);

        categoria.editar("Uber", TipoCategoria.DESPESA, null);

        assertThat(categoria.getCategoriaPaiId()).isNull();
    }
}
