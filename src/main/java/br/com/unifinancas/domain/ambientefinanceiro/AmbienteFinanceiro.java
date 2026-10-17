package br.com.unifinancas.domain.ambientefinanceiro;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
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
 * Tenant principal - dono logico de todos os dados financeiros de um
 * contexto (SAD Sec4.2). Assim como {@code Usuario} nao guarda uma colecao
 * de vinculos (RN-02): quais usuarios pertencem a este ambiente e uma
 * pergunta para o repositorio de {@code UsuarioAmbienteFinanceiro}.
 */
@Entity
@Table(name = "ambiente_financeiro")
public class AmbienteFinanceiro {

    private static final int DIAS_ANTECEDENCIA_PADRAO = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAmbienteFinanceiro tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanoAmbienteFinanceiro plano;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAmbienteFinanceiro status;

    @Column(name = "dias_antecedencia_notificacao_vencimento", nullable = false)
    private int diasAntecedenciaNotificacaoVencimento;

    @Column(name = "ocr_fallback_ia_habilitado", nullable = false)
    private boolean ocrFallbackIaHabilitado;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected AmbienteFinanceiro() {
        // exigido pelo JPA
    }

    public static AmbienteFinanceiro novo(String nome, TipoAmbienteFinanceiro tipo) {
        return novo(nome, tipo, null);
    }

    /**
     * T1.5.4, SAD Sec6.5: {@code diasAntecedenciaNotificacaoVencimento} e
     * opcional (nulo usa o padrao de 3 dias) - configuravel por Ambiente
     * Financeiro (decisao consultada com o usuario), usado pelo job diario
     * de vencimento para decidir quando um Lancamento esta "proximo".
     */
    public static AmbienteFinanceiro novo(String nome, TipoAmbienteFinanceiro tipo, Integer diasAntecedenciaNotificacaoVencimento) {
        AmbienteFinanceiro ambiente = new AmbienteFinanceiro();
        ambiente.nome = nome;
        ambiente.tipo = tipo;
        ambiente.plano = PlanoAmbienteFinanceiro.GRATUITO;
        ambiente.status = StatusAmbienteFinanceiro.ATIVO;
        ambiente.diasAntecedenciaNotificacaoVencimento = validarDiasAntecedencia(diasAntecedenciaNotificacaoVencimento);
        ambiente.ocrFallbackIaHabilitado = false;
        Instant agora = Instant.now();
        ambiente.criadoEm = agora;
        ambiente.atualizadoEm = agora;
        return ambiente;
    }

    /**
     * T1.11.4, RN-13 (RNF-09/LGPD, decisao consultada com o usuario): quando
     * o OWNER de um Ambiente Financeiro exclui sua conta, o Ambiente inteiro
     * e encerrado junto - nao so o vinculo do OWNER que saiu. Remover so o
     * vinculo do OWNER deixaria o Ambiente orfao (sem ninguem para
     * gerenciar) ou exigiria escolher um novo dono sem o consentimento de
     * ninguem; encerrar de vez e a alternativa mais simples e menos
     * surpreendente. Quem remove os vinculos dos demais membros e {@code
     * PrivacidadeService.excluirConta} (nao esta entidade - remover um
     * vinculo e responsabilidade de {@link
     * br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro#remover()}).
     */
    public void encerrar() {
        this.status = StatusAmbienteFinanceiro.ENCERRADO;
        this.atualizadoEm = Instant.now();
    }

    /**
     * Gap identificado pelo usuario (nao havia edicao de Ambiente Financeiro
     * pela UI nem pela API): altera nome e dias de antecedencia. {@code tipo}
     * nao e editavel aqui - e so metadado sem regra de negocio associada
     * (ADR-008), nao ha motivo para trocar depois de criado.
     */
    public void editar(String nome, Integer diasAntecedenciaNotificacaoVencimento) {
        this.nome = nome;
        this.diasAntecedenciaNotificacaoVencimento = validarDiasAntecedencia(diasAntecedenciaNotificacaoVencimento);
        this.atualizadoEm = Instant.now();
    }

    /**
     * ADR-016 (T3.1.2): liga/desliga o ultimo recurso da cascata de extracao de
     * documentos - envio a OpenAI API quando XML/PDFBox/OCR proprio falham.
     * Default {@code false} na criacao; opt-in explicito por Ambiente Financeiro,
     * porque envia o documento financeiro a um terceiro. So ter a chave
     * {@code OPENAI_API_KEY} no backend nao basta - esta flag tambem precisa
     * estar ligada. A UI/endpoint que expoe isso vem em tarefa posterior.
     */
    public void definirOcrFallbackIaHabilitado(boolean habilitado) {
        this.ocrFallbackIaHabilitado = habilitado;
        this.atualizadoEm = Instant.now();
    }

    private static int validarDiasAntecedencia(Integer dias) {
        if (dias == null) {
            return DIAS_ANTECEDENCIA_PADRAO;
        }
        if (dias <= 0) {
            throw new RegraDeNegocioException("DIAS_ANTECEDENCIA_INVALIDO",
                    "diasAntecedenciaNotificacaoVencimento deve ser maior que zero.");
        }
        return dias;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public TipoAmbienteFinanceiro getTipo() {
        return tipo;
    }

    public PlanoAmbienteFinanceiro getPlano() {
        return plano;
    }

    public StatusAmbienteFinanceiro getStatus() {
        return status;
    }

    public int getDiasAntecedenciaNotificacaoVencimento() {
        return diasAntecedenciaNotificacaoVencimento;
    }

    public boolean isOcrFallbackIaHabilitado() {
        return ocrFallbackIaHabilitado;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
