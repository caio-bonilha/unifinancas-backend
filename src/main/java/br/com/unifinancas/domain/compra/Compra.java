package br.com.unifinancas.domain.compra;

import br.com.unifinancas.domain.lancamento.FormaPagamento;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Evento comercial de aquisicao, com itens (RF-18, SAD Sec4.8). Agregado
 * raiz: {@code CompraItem} so existe dentro de uma Compra (sem repositorio
 * proprio) - carregado sempre por inteiro ({@code FetchType.EAGER}, sem
 * open-session-in-view no projeto) porque a geracao de Lancamento a partir
 * de uma Compra (T2.2.2, fora de escopo desta tarefa) sempre precisa dos
 * itens.
 */
@Entity
@Table(name = "compra")
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    /** Estabelecimento/fornecedor - opcional (Sec4.8). */
    @Column(name = "contato_id")
    private UUID contatoId;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 20)
    private FormaPagamento formaPagamento;

    /** So preenchido quando formaPagamento = CARTAO_CREDITO (validado em {@link #novo}). */
    @Column(name = "cartao_id")
    private UUID cartaoId;

    @Column(nullable = false)
    private int parcelas;

    /** Origem, ex.: cupom fiscal (RF-20) - opcional. */
    @Column(name = "documento_id")
    private UUID documentoId;

    /**
     * `[Revisado - T2.2.2]` opcional - decisao consultada com o usuario:
     * quando informado, vira a descricao do(s) Lancamento(s) gerados por
     * {@code CompraVendaService}; quando ausente, a descricao e derivada dos
     * itens (nome do primeiro item, "e mais N itens" se houver mais de um).
     */
    @Column(length = 200)
    private String descricao;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "compra_id", nullable = false)
    private List<CompraItem> itens = new ArrayList<>();

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Compra() {
        // exigido pelo JPA
    }

    /**
     * {@code valorTotal} e sempre a soma de {@code itens} (nunca informado
     * pelo chamador - uma unica fonte de verdade, mesmo criterio de
     * {@code CompraItem.valorTotal}).
     */
    public static Compra novo(UUID ambienteFinanceiroId, UUID contatoId, LocalDate data, FormaPagamento formaPagamento,
            UUID cartaoId, Integer parcelas, UUID documentoId, String descricao, List<CompraItem> itens) {
        Compra compra = new Compra();
        compra.ambienteFinanceiroId = ambienteFinanceiroId;
        compra.contatoId = contatoId;
        compra.data = validarData(data);
        compra.itens = validarItens(itens);
        compra.valorTotal = somarItens(compra.itens);
        compra.formaPagamento = formaPagamento;
        compra.cartaoId = validarCartao(formaPagamento, cartaoId);
        compra.parcelas = validarParcelas(parcelas);
        compra.documentoId = documentoId;
        compra.descricao = descricao != null && !descricao.isBlank() ? descricao : null;
        Instant agora = Instant.now();
        compra.criadoEm = agora;
        compra.atualizadoEm = agora;
        return compra;
    }

    private static LocalDate validarData(LocalDate data) {
        if (data == null) {
            throw new RegraDeNegocioException("DATA_OBRIGATORIA", "data e obrigatoria.");
        }
        return data;
    }

    private static List<CompraItem> validarItens(List<CompraItem> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new RegraDeNegocioException("ITENS_OBRIGATORIOS", "Uma Compra deve ter pelo menos um item.");
        }
        return new ArrayList<>(itens);
    }

    private static BigDecimal somarItens(List<CompraItem> itens) {
        return itens.stream().map(CompraItem::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static UUID validarCartao(FormaPagamento formaPagamento, UUID cartaoId) {
        boolean cartaoCredito = formaPagamento == FormaPagamento.CARTAO_CREDITO;
        if (cartaoCredito && cartaoId == null) {
            throw new RegraDeNegocioException(
                    "CARTAO_OBRIGATORIO", "cartaoId e obrigatorio quando formaPagamento e CARTAO_CREDITO.");
        }
        if (!cartaoCredito && cartaoId != null) {
            throw new RegraDeNegocioException("CARTAO_INCOMPATIVEL_COM_FORMA_PAGAMENTO",
                    "cartaoId so pode ser informado quando formaPagamento e CARTAO_CREDITO.");
        }
        return cartaoId;
    }

    private static int validarParcelas(Integer parcelas) {
        int valor = parcelas != null ? parcelas : 1;
        if (valor < 1) {
            throw new RegraDeNegocioException("PARCELAS_INVALIDAS", "parcelas deve ser pelo menos 1.");
        }
        return valor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAmbienteFinanceiroId() {
        return ambienteFinanceiroId;
    }

    public UUID getContatoId() {
        return contatoId;
    }

    public LocalDate getData() {
        return data;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public UUID getCartaoId() {
        return cartaoId;
    }

    public int getParcelas() {
        return parcelas;
    }

    public UUID getDocumentoId() {
        return documentoId;
    }

    public String getDescricao() {
        return descricao;
    }

    public List<CompraItem> getItens() {
        return Collections.unmodifiableList(itens);
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
