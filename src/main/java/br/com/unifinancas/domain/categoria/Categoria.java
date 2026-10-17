package br.com.unifinancas.domain.categoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Classificacao hierarquica das movimentacoes (RF-09, SAD Sec4.10).
 * {@code categoriaPaiId} referencia outra Categoria pelo id (auto-relacionamento
 * via coluna simples, nao {@code @ManyToOne}) - mesma escolha de
 * {@code UsuarioAmbienteFinanceiro} para usuario/ambiente: evita lazy loading
 * desnecessario para algo sempre resolvido pelo id.
 */
@Entity
@Table(name = "categoria")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    @Column(nullable = false, length = 100)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCategoria tipo;

    @Column(name = "categoria_pai_id")
    private UUID categoriaPaiId;

    @Column(nullable = false)
    private boolean ativa;

    protected Categoria() {
        // exigido pelo JPA
    }

    public static Categoria novo(UUID ambienteFinanceiroId, String nome, TipoCategoria tipo, UUID categoriaPaiId) {
        Categoria categoria = new Categoria();
        categoria.ambienteFinanceiroId = ambienteFinanceiroId;
        categoria.nome = nome;
        categoria.tipo = tipo;
        categoria.categoriaPaiId = categoriaPaiId;
        categoria.ativa = true;
        return categoria;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAmbienteFinanceiroId() {
        return ambienteFinanceiroId;
    }

    public String getNome() {
        return nome;
    }

    public TipoCategoria getTipo() {
        return tipo;
    }

    public UUID getCategoriaPaiId() {
        return categoriaPaiId;
    }

    public boolean isAtiva() {
        return ativa;
    }

    /**
     * [Gap identificado pelo usuario]: categoriaPaiId agora e editavel - a
     * deteccao de ciclo (uma categoria nao pode virar sua propria
     * descendente) exige andar pela hierarquia de outras Categorias, o que
     * esta entidade nao tem como fazer sozinha (sem acesso ao repositorio);
     * {@link br.com.unifinancas.application.categoria.CategoriaService#editar}
     * valida isso antes de chamar este metodo.
     */
    public void editar(String nome, TipoCategoria tipo, UUID categoriaPaiId) {
        this.nome = nome;
        this.tipo = tipo;
        this.categoriaPaiId = categoriaPaiId;
    }

    public void ativar() {
        this.ativa = true;
    }

    public void desativar() {
        this.ativa = false;
    }
}
