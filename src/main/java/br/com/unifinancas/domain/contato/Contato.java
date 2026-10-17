package br.com.unifinancas.domain.contato;

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
 * Cliente/fornecedor associado a operacoes financeiras (RF-10, SAD Sec4.12).
 * {@code documento} (CPF/CNPJ) nao recebe criptografia de coluna adicional no
 * MVP (Sec8.4) - nao e uma credencial financeira de altissima sensibilidade.
 */
@Entity
@Table(name = "contato")
public class Contato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    @Column(nullable = false, length = 150)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoContato tipo;

    @Column(length = 20)
    private String documento;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(nullable = false)
    private boolean ativo;

    protected Contato() {
        // exigido pelo JPA
    }

    public static Contato novo(UUID ambienteFinanceiroId, String nome, TipoContato tipo, String documento,
            String email, String telefone) {
        Contato contato = new Contato();
        contato.ambienteFinanceiroId = ambienteFinanceiroId;
        contato.nome = nome;
        contato.tipo = tipo;
        contato.documento = documento;
        contato.email = email;
        contato.telefone = telefone;
        contato.ativo = true;
        return contato;
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

    public TipoContato getTipo() {
        return tipo;
    }

    public String getDocumento() {
        return documento;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public boolean isAtivo() {
        return ativo;
    }

    /** T1.9.6 (gap identificado ao planejar a tela - T1.2.3 so tinha criar e listar). */
    public void editar(String nome, TipoContato tipo, String documento, String email, String telefone) {
        this.nome = nome;
        this.tipo = tipo;
        this.documento = documento;
        this.email = email;
        this.telefone = telefone;
    }

    public void ativar() {
        this.ativo = true;
    }

    public void desativar() {
        this.ativo = false;
    }
}
