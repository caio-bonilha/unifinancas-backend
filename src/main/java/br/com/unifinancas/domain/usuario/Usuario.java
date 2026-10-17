package br.com.unifinancas.domain.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Identidade global do sistema, independente de qualquer Ambiente Financeiro
 * (SAD Sec4.1). Nao carrega uma colecao de {@code UsuarioAmbienteFinanceiro}
 * de proposito: RN-01 diz que um usuario pode ter zero, uma ou varias
 * associacoes, sem limite algum a impor aqui - quais ambientes um usuario
 * pertence e uma pergunta para o repositorio de UsuarioAmbienteFinanceiro
 * responder, nao uma colecao carregada em memoria junto com o Usuario.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 255)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusUsuario status;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Usuario() {
        // exigido pelo JPA
    }

    public static Usuario novo(String nome, String email, String senhaHash) {
        Usuario usuario = new Usuario();
        usuario.nome = nome;
        usuario.email = email;
        usuario.senhaHash = senhaHash;
        usuario.status = StatusUsuario.ATIVO;
        Instant agora = Instant.now();
        usuario.criadoEm = agora;
        usuario.atualizadoEm = agora;
        return usuario;
    }

    /**
     * RN-13, T1.11.4 (RNF-09/LGPD, decisao consultada com o usuario):
     * soft-delete + anonimizacao, nao remocao fisica da linha - nome/e-mail/
     * senha viram valores sem nenhum dado pessoal identificavel, e o login
     * para de funcionar (so usuarios ATIVO autenticam - {@code
     * IdentidadeService.autenticar}). O que permanece (id, trilha de
     * auditoria RNF-07, Lancamentos que este usuario criou em Ambientes
     * Financeiros compartilhados onde ele nao era OWNER) fica sem
     * identificar a pessoa, preservando o historico financeiro de quem
     * continua no Ambiente. Terminal - chamar de novo em um Usuario ja
     * EXCLUIDO e um erro de programacao (quem chama, {@code
     * PrivacidadeService}, ja deve ter validado o status antes).
     */
    public void excluir() {
        this.nome = "Usuario excluido";
        this.email = "excluido+" + id + "@unifinancas.invalido";
        this.senhaHash = "";
        this.status = StatusUsuario.EXCLUIDO;
        this.atualizadoEm = Instant.now();
    }

    /**
     * T1.1.10 (SAD Sec6.7/6.8, RN-14): troca o hash da senha - tanto pela
     * redefinicao via e-mail quanto pela alteracao do usuario autenticado.
     * Quem chama ({@code IdentidadeService}) ja aplicou a politica de senha e
     * ja codificou o hash, e revoga os refresh tokens do Usuario logo depois.
     */
    public void alterarSenha(String novaSenhaHash) {
        this.senhaHash = novaSenhaHash;
        this.atualizadoEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public StatusUsuario getStatus() {
        return status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
