package br.com.unifinancas.domain.venda;

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
 * Evento comercial de recebimento, com itens (RF-19, SAD Sec4.9) - simetrico
 * a {@code Compra}, mas sem {@code cartaoId}/{@code parcelas} (Sec5.2: quem
 * parcela e quem compra, nao quem vende) e sem {@code descricao} ainda
 * (mesmo sequenciamento incremental de {@code Compra}: a decisao so foi
 * tomada quando a geracao de Lancamento, T2.2.2, precisou de uma - aqui
 * fica para T2.3.2). Agregado raiz: {@code VendaItem} so existe dentro de
 * uma Venda (sem repositorio proprio) - carregado sempre por inteiro
 * ({@code FetchType.EAGER}, sem open-session-in-view no projeto), mesmo
 * criterio de {@code Compra}.
 */
@Entity
@Table(name = "venda")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    /** Cliente - opcional (Sec4.9). */
    @Column(name = "contato_id")
    private UUID contatoId;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 20)
    private FormaPagamento formaPagamento;

    /** Origem, ex.: nota fiscal emitida - opcional. */
    @Column(name = "documento_id")
    private UUID documentoId;

    /**
     * `[Revisado - T2.3.2]` opcional - mesma decisao ja tomada para
     * {@code Compra.descricao} (T2.2.2), aplicada ao par simetrico: quando
     * informado, vira a descricao do Lancamento gerado por
     * {@code CompraVendaService}; quando ausente, a descricao e derivada
     * dos itens (nome do primeiro item, "e mais N itens" se houver mais de
     * um).
     */
    @Column(length = 200)
    private String descricao;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "venda_id", nullable = false)
    private List<VendaItem> itens = new ArrayList<>();

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Venda() {
        // exigido pelo JPA
    }

    /**
     * {@code valorTotal} e sempre a soma de {@code itens} (nunca informado
     * pelo chamador - uma unica fonte de verdade, mesmo criterio de
     * {@code Compra.novo}).
     */
    public static Venda novo(UUID ambienteFinanceiroId, UUID contatoId, LocalDate data, FormaPagamento formaPagamento,
            UUID documentoId, String descricao, List<VendaItem> itens) {
        Venda venda = new Venda();
        venda.ambienteFinanceiroId = ambienteFinanceiroId;
        venda.contatoId = contatoId;
        venda.data = validarData(data);
        venda.itens = validarItens(itens);
        venda.valorTotal = somarItens(venda.itens);
        venda.formaPagamento = formaPagamento;
        venda.documentoId = documentoId;
        venda.descricao = descricao != null && !descricao.isBlank() ? descricao : null;
        Instant agora = Instant.now();
        venda.criadoEm = agora;
        venda.atualizadoEm = agora;
        return venda;
    }

    private static LocalDate validarData(LocalDate data) {
        if (data == null) {
            throw new RegraDeNegocioException("DATA_OBRIGATORIA", "data e obrigatoria.");
        }
        return data;
    }

    private static List<VendaItem> validarItens(List<VendaItem> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new RegraDeNegocioException("ITENS_OBRIGATORIOS", "Uma Venda deve ter pelo menos um item.");
        }
        return new ArrayList<>(itens);
    }

    private static BigDecimal somarItens(List<VendaItem> itens) {
        return itens.stream().map(VendaItem::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
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

    public UUID getDocumentoId() {
        return documentoId;
    }

    public String getDescricao() {
        return descricao;
    }

    public List<VendaItem> getItens() {
        return Collections.unmodifiableList(itens);
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
