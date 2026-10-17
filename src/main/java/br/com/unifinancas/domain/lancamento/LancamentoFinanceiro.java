package br.com.unifinancas.domain.lancamento;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Nucleo transacional unico do sistema - unidade de receita ou despesa
 * (RF-06, SAD Sec4.7). Mapeia a tabela {@code lancamento} (T1.3.1) por
 * inteiro, incluindo as colunas de origem futura (cartaoId, faturaId,
 * compraId, vendaId) que ainda nao tem FK no banco -
 * {@link #novo} (origem MANUAL) e {@link #novoDeRecorrencia} (origem
 * RECORRENCIA, T1.6.3) existem ate agora; as demais origens ganham sua
 * propria factory quando a tarefa correspondente (Etapa 1.8, Fase 2) existir.
 *
 * <p>[Revisado - ADR-012] Nao representa mais transferencia entre contas -
 * a Movimentacao de Conta Bancaria vinculada a este Lancamento quando ele e
 * liquidado (RN-12) e criada e removida pelo {@code LancamentoService}, nao
 * por esta entidade (envolve persistencia, fora do dominio puro).
 */
@Entity
@Table(name = "lancamento")
public class LancamentoFinanceiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "ambiente_financeiro_id", nullable = false)
    private UUID ambienteFinanceiroId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoLancamento tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusLancamento status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Column(name = "categoria_id")
    private UUID categoriaId;

    @Column(name = "contato_id")
    private UUID contatoId;

    @Column(name = "conta_bancaria_id")
    private UUID contaBancariaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 20)
    private FormaPagamento formaPagamento;

    /** Sem FK no banco ainda (T1.3.1) - tabela cartao nasce na T2.1.1. */
    @Column(name = "cartao_id")
    private UUID cartaoId;

    /** Sem FK no banco ainda (T1.3.1) - tabela fatura nasce na T2.1.1. */
    @Column(name = "fatura_id")
    private UUID faturaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemLancamento origem;

    /** Sem FK no banco ainda (T1.3.1) - tabela lancamento_recorrente nasce na T1.6.1. */
    @Column(name = "lancamento_recorrente_id")
    private UUID lancamentoRecorrenteId;

    /** Sem FK no banco ainda (T1.3.1) - tabela compra nasce na T2.2.1. */
    @Column(name = "compra_id")
    private UUID compraId;

    /** Sem FK no banco ainda (T1.3.1) - tabela venda nasce na T2.3.1. */
    @Column(name = "venda_id")
    private UUID vendaId;

    /**
     * [Gap identificado pelo usuario - T1.3.1/Sec4.7 so tinham observacao]:
     * rotulo curto e obrigatorio do Lancamento (ex.: "Uber para o
     * aeroporto") - vira o texto principal nas listas, diferente de
     * observacao (nota livre opcional).
     */
    @Column(nullable = false, length = 200)
    private String descricao;

    @Column
    private String observacao;

    /**
     * Etapa A (pedido do usuario): linha digitavel do boleto/conta - 47
     * digitos (cobranca bancaria) ou 48 (arrecadacao). Guardada so com
     * digitos; opcional. Vem preenchida quando o Lancamento nasce de um
     * documento processado (T3.1.5).
     */
    @Column(name = "linha_digitavel", length = 48)
    private String linhaDigitavel;

    /**
     * T4.1.3, RF-21, RNF-02: identificador de origem de um Lancamento
     * nascido de importacao (T4.1.4, ainda sem factory propria) - o FITID
     * do OFX, ou um hash de contaBancariaId+data+valor+descricao quando a
     * origem (CSV/Excel) nao tem identificador proprio
     * ({@code ImportacaoService}). Usado para detectar reimportacao do
     * mesmo extrato. Nulo para qualquer outra origem.
     */
    @Column(name = "identificador_externo", length = 100)
    private String identificadorExterno;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected LancamentoFinanceiro() {
        // exigido pelo JPA
    }

    /**
     * Cria um Lancamento de origem MANUAL (RF-06, Fluxo 6.2) - nasce sempre
     * PENDENTE (Sec6.5).
     */
    public static LancamentoFinanceiro novo(UUID ambienteFinanceiroId, TipoLancamento tipo, BigDecimal valor,
            LocalDate dataEmissao, LocalDate dataVencimento, UUID categoriaId, UUID contatoId, UUID contaBancariaId,
            FormaPagamento formaPagamento, String descricao, String observacao) {
        validarValor(valor);
        validarDescricao(descricao);

        LancamentoFinanceiro lancamento = new LancamentoFinanceiro();
        lancamento.ambienteFinanceiroId = ambienteFinanceiroId;
        lancamento.tipo = tipo;
        lancamento.status = StatusLancamento.PENDENTE;
        lancamento.valor = valor;
        lancamento.dataEmissao = dataEmissao;
        lancamento.dataVencimento = dataVencimento;
        lancamento.categoriaId = categoriaId;
        lancamento.contatoId = contatoId;
        lancamento.contaBancariaId = contaBancariaId;
        lancamento.formaPagamento = formaPagamento;
        lancamento.origem = OrigemLancamento.MANUAL;
        lancamento.descricao = descricao;
        lancamento.observacao = observacao;
        Instant agora = Instant.now();
        lancamento.criadoEm = agora;
        lancamento.atualizadoEm = agora;
        return lancamento;
    }

    /**
     * T1.6.3, Fluxo 6.6: cria um Lancamento de origem RECORRENCIA,
     * materializado pelo {@code RecorrenciaJob} a partir de um Lancamento
     * Recorrente ativo - mesmas invariantes de {@link #novo}, guardando
     * tambem o vinculo com a recorrencia de origem. Sem {@code contatoId}/
     * {@code formaPagamento}: SAD Sec4.13 nao lista esses atributos em
     * Lancamento Recorrente.
     */
    public static LancamentoFinanceiro novoDeRecorrencia(UUID ambienteFinanceiroId, UUID lancamentoRecorrenteId,
            TipoLancamento tipo, BigDecimal valor, LocalDate dataEmissao, LocalDate dataVencimento, UUID categoriaId,
            UUID contaBancariaId, String descricao, String observacao) {
        validarValor(valor);
        validarDescricao(descricao);

        LancamentoFinanceiro lancamento = new LancamentoFinanceiro();
        lancamento.ambienteFinanceiroId = ambienteFinanceiroId;
        lancamento.tipo = tipo;
        lancamento.status = StatusLancamento.PENDENTE;
        lancamento.valor = valor;
        lancamento.dataEmissao = dataEmissao;
        lancamento.dataVencimento = dataVencimento;
        lancamento.categoriaId = categoriaId;
        lancamento.contaBancariaId = contaBancariaId;
        lancamento.origem = OrigemLancamento.RECORRENCIA;
        lancamento.lancamentoRecorrenteId = lancamentoRecorrenteId;
        lancamento.descricao = descricao;
        lancamento.observacao = observacao;
        Instant agora = Instant.now();
        lancamento.criadoEm = agora;
        lancamento.atualizadoEm = agora;
        return lancamento;
    }

    /**
     * ADR-017, T2.1.3/T2.1.4: cria um Lancamento DESPESA de origem FATURA -
     * o juros rotativo de uma Fatura paga parcialmente, ou uma das N
     * parcelas do parcelamento de uma Fatura em atraso. Sempre PENDENTE,
     * sem contaBancariaId/formaPagamento na criacao (ADR-015: so exigidos
     * na confirmacao de pagamento, igual a qualquer outro Lancamento).
     */
    public static LancamentoFinanceiro novoDeFatura(UUID ambienteFinanceiroId, UUID cartaoId, UUID faturaId,
            BigDecimal valor, LocalDate dataEmissao, LocalDate dataVencimento, String descricao) {
        validarValor(valor);
        validarDescricao(descricao);

        LancamentoFinanceiro lancamento = new LancamentoFinanceiro();
        lancamento.ambienteFinanceiroId = ambienteFinanceiroId;
        lancamento.tipo = TipoLancamento.DESPESA;
        lancamento.status = StatusLancamento.PENDENTE;
        lancamento.valor = valor;
        lancamento.dataEmissao = dataEmissao;
        lancamento.dataVencimento = dataVencimento;
        lancamento.cartaoId = cartaoId;
        lancamento.faturaId = faturaId;
        lancamento.origem = OrigemLancamento.FATURA;
        lancamento.descricao = descricao;
        Instant agora = Instant.now();
        lancamento.criadoEm = agora;
        lancamento.atualizadoEm = agora;
        return lancamento;
    }

    /**
     * T2.2.2, RF-18, SAD Sec4.8: cria um Lancamento DESPESA de origem COMPRA
     * - sempre PENDENTE (mesmo criterio de {@link #novoDeFatura}), mesmo
     * quando a Compra ja foi paga na hora (DINHEIRO/DEBITO/PIX): a
     * confirmacao de pagamento continua sendo um passo explicito e
     * separado, igual a qualquer outra origem (RECORRENCIA, FATURA,
     * DOCUMENTO). {@code cartaoId}/{@code faturaId} so quando a Compra foi
     * no cartao de credito (nulos caso contrario - CompraVendaService
     * decide).
     */
    public static LancamentoFinanceiro novoDeCompra(UUID ambienteFinanceiroId, UUID compraId, UUID contatoId,
            UUID cartaoId, UUID faturaId, BigDecimal valor, LocalDate dataEmissao, LocalDate dataVencimento,
            String descricao) {
        validarValor(valor);
        validarDescricao(descricao);

        LancamentoFinanceiro lancamento = new LancamentoFinanceiro();
        lancamento.ambienteFinanceiroId = ambienteFinanceiroId;
        lancamento.tipo = TipoLancamento.DESPESA;
        lancamento.status = StatusLancamento.PENDENTE;
        lancamento.valor = valor;
        lancamento.dataEmissao = dataEmissao;
        lancamento.dataVencimento = dataVencimento;
        lancamento.contatoId = contatoId;
        lancamento.cartaoId = cartaoId;
        lancamento.faturaId = faturaId;
        lancamento.compraId = compraId;
        lancamento.origem = OrigemLancamento.COMPRA;
        lancamento.descricao = descricao;
        Instant agora = Instant.now();
        lancamento.criadoEm = agora;
        lancamento.atualizadoEm = agora;
        return lancamento;
    }

    /**
     * T2.3.2, RF-19, SAD Sec4.9: cria o (unico) Lancamento RECEITA de
     * origem VENDA - sempre PENDENTE (mesmo criterio de
     * {@link #novoDeCompra}), mesmo quando a Venda ja foi recebida na hora.
     * Diferente de Compra, Venda nao tem cartaoId/parcelas (Sec5.2: quem
     * parcela e quem compra, nao quem vende) - por isso gera sempre um
     * unico Lancamento, nunca N.
     */
    public static LancamentoFinanceiro novoDeVenda(UUID ambienteFinanceiroId, UUID vendaId, UUID contatoId,
            BigDecimal valor, LocalDate dataEmissao, LocalDate dataVencimento, String descricao) {
        validarValor(valor);
        validarDescricao(descricao);

        LancamentoFinanceiro lancamento = new LancamentoFinanceiro();
        lancamento.ambienteFinanceiroId = ambienteFinanceiroId;
        lancamento.tipo = TipoLancamento.RECEITA;
        lancamento.status = StatusLancamento.PENDENTE;
        lancamento.valor = valor;
        lancamento.dataEmissao = dataEmissao;
        lancamento.dataVencimento = dataVencimento;
        lancamento.contatoId = contatoId;
        lancamento.vendaId = vendaId;
        lancamento.origem = OrigemLancamento.VENDA;
        lancamento.descricao = descricao;
        Instant agora = Instant.now();
        lancamento.criadoEm = agora;
        lancamento.atualizadoEm = agora;
        return lancamento;
    }

    /**
     * T4.1.4, RF-21 (decisao consultada com o usuario): cria um Lancamento
     * de origem IMPORTACAO - diferente de qualquer outra origem, nasce
     * JA {@code PAGO}/{@code RECEBIDO} (nunca PENDENTE): uma linha de
     * extrato bancario representa algo que ja aconteceu, o dinheiro ja se
     * moveu, entao exigir uma confirmacao manual separada por linha
     * importada seria friccao sem necessidade. {@code contaBancariaId} ja
     * e conhecido (o usuario escolhe a Conta Bancaria ao importar);
     * {@code formaPagamento} fixo {@code TRANSFERENCIA} - o extrato nao
     * distingue a forma de pagamento com confiabilidade suficiente pra
     * escolher entre as demais opcoes. {@code identificadorExterno}: ver
     * {@code ImportacaoService} (FITID do OFX, ou hash sintetico pra
     * CSV/Excel) - usado pela deteccao de duplicidade entre importacoes
     * (RNF-02).
     */
    public static LancamentoFinanceiro novoDeImportacao(UUID ambienteFinanceiroId, TipoLancamento tipo, BigDecimal valor,
            LocalDate data, UUID categoriaId, UUID contaBancariaId, String descricao, String identificadorExterno) {
        validarValor(valor);
        validarDescricao(descricao);

        LancamentoFinanceiro lancamento = new LancamentoFinanceiro();
        lancamento.ambienteFinanceiroId = ambienteFinanceiroId;
        lancamento.tipo = tipo;
        lancamento.status = tipo == TipoLancamento.DESPESA ? StatusLancamento.PAGO : StatusLancamento.RECEBIDO;
        lancamento.valor = valor;
        lancamento.dataEmissao = data;
        lancamento.dataVencimento = data;
        lancamento.dataPagamento = data;
        lancamento.categoriaId = categoriaId;
        lancamento.contaBancariaId = contaBancariaId;
        lancamento.formaPagamento = FormaPagamento.TRANSFERENCIA;
        lancamento.origem = OrigemLancamento.IMPORTACAO;
        lancamento.descricao = descricao;
        lancamento.identificadorExterno = identificadorExterno;
        Instant agora = Instant.now();
        lancamento.criadoEm = agora;
        lancamento.atualizadoEm = agora;
        return lancamento;
    }

    /**
     * T3.1.5, SAD Fluxo 6.3 (RN-08): cria um Lancamento de origem DOCUMENTO a
     * partir dos dados que o usuario revisou e confirmou sobre um documento
     * processado (boleto, nota, comprovante). Nunca e automatico - so acontece
     * na confirmacao explicita. `[Revisado - Etapa B]` O vinculo com o
     * Documento de origem passou para {@code documento.lancamento_id} (N:1) e
     * e gravado pelo {@code ProcessamentoDocumentoService} logo apos criar
     * este Lancamento - nao mora mais aqui.
     */
    public static LancamentoFinanceiro novoDeDocumento(UUID ambienteFinanceiroId, TipoLancamento tipo, BigDecimal valor,
            LocalDate dataEmissao, LocalDate dataVencimento, UUID categoriaId, UUID contatoId, UUID contaBancariaId,
            FormaPagamento formaPagamento, String descricao, String observacao) {
        validarValor(valor);
        validarDescricao(descricao);

        LancamentoFinanceiro lancamento = new LancamentoFinanceiro();
        lancamento.ambienteFinanceiroId = ambienteFinanceiroId;
        lancamento.tipo = tipo;
        lancamento.status = StatusLancamento.PENDENTE;
        lancamento.valor = valor;
        lancamento.dataEmissao = dataEmissao;
        lancamento.dataVencimento = dataVencimento;
        lancamento.categoriaId = categoriaId;
        lancamento.contatoId = contatoId;
        lancamento.contaBancariaId = contaBancariaId;
        lancamento.formaPagamento = formaPagamento;
        lancamento.origem = OrigemLancamento.DOCUMENTO;
        lancamento.descricao = descricao;
        lancamento.observacao = observacao;
        Instant agora = Instant.now();
        lancamento.criadoEm = agora;
        lancamento.atualizadoEm = agora;
        return lancamento;
    }

    private static void validarValor(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException("VALOR_INVALIDO", "O valor do lancamento deve ser maior que zero.");
        }
    }

    /** Gap identificado pelo usuario: descricao e obrigatoria (mesmo criterio de validarValor - sem espaco em branco). */
    private static void validarDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            throw new RegraDeNegocioException("DESCRICAO_OBRIGATORIA", "Informe a descricao do lancamento.");
        }
    }

    /** Guarda so os digitos (o usuario pode colar "23793.38128 60082..."); vazio vira nulo. Sem rejeitar tamanho - a exibicao trata o que nao decodifica. */
    private static String normalizarLinhaDigitavel(String bruta) {
        if (bruta == null) {
            return null;
        }
        String digitos = bruta.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    /**
     * RN-06: conta a pagar e uma visao filtrada sobre Lancamento (tipo
     * DESPESA ainda nao liquidado), nao uma entidade de persistencia
     * separada - quem monta a visao/endpoint de "contas a pagar" (T1.3.5)
     * filtra por este predicado, nao por uma tabela propria.
     */
    public boolean ehContaAPagar() {
        return tipo == TipoLancamento.DESPESA && aindaNaoLiquidado();
    }

    /** RN-06: mesma logica de {@link #ehContaAPagar()}, para o lado RECEITA. */
    public boolean ehContaAReceber() {
        return tipo == TipoLancamento.RECEITA && aindaNaoLiquidado();
    }

    private boolean aindaNaoLiquidado() {
        return status == StatusLancamento.PENDENTE || status == StatusLancamento.ATRASADO;
    }

    /**
     * Edita os campos descritivos (T1.3.3) - {@code tipo} nunca muda depois
     * de criado (trocar DESPESA por RECEITA nao faz sentido; cancelar e
     * recriar e o caminho certo). So permitido enquanto ainda nao foi
     * liquidado.
     */
    public void editar(BigDecimal valor, LocalDate dataEmissao, LocalDate dataVencimento, UUID categoriaId,
            UUID contatoId, UUID contaBancariaId, FormaPagamento formaPagamento, String descricao, String observacao) {
        validarEditavel();
        validarValor(valor);
        validarDescricao(descricao);

        this.valor = valor;
        this.dataEmissao = dataEmissao;
        this.dataVencimento = dataVencimento;
        this.categoriaId = categoriaId;
        this.contatoId = contatoId;
        this.contaBancariaId = contaBancariaId;
        this.formaPagamento = formaPagamento;
        this.descricao = descricao;
        this.observacao = observacao;
        this.atualizadoEm = Instant.now();
    }

    /**
     * Etapa A: define/atualiza a linha digitável (pode ser chamada logo após
     * {@link #novo}/{@link #novoDeDocumento} ou na edição). Guarda só dígitos;
     * vazio limpa o campo.
     */
    public void definirLinhaDigitavel(String bruta) {
        this.linhaDigitavel = normalizarLinhaDigitavel(bruta);
        this.atualizadoEm = Instant.now();
    }

    /**
     * Sec6.5: PENDENTE/ATRASADO -> PAGO ("confirmacao de pagamento"), so
     * para DESPESA. `[Revisado - ADR-015]` {@code contaBancariaId}/
     * {@code formaPagamento} sao resolvidos por campo (ver
     * {@link #resolverContaBancariaParaQuitacao}/{@link #resolverFormaPagamentoParaQuitacao}):
     * se o Lancamento ja tem o campo preenchido, o parametro e ignorado; se
     * nao tem, o parametro e obrigatorio e passa a ser gravado.
     */
    public void confirmarPagamento(LocalDate dataPagamento, UUID contaBancariaId, FormaPagamento formaPagamento) {
        validarTipoParaConfirmacao(TipoLancamento.DESPESA);
        validarTransicaoParaLiquidado();
        this.contaBancariaId = resolverContaBancariaParaQuitacao(contaBancariaId);
        this.formaPagamento = resolverFormaPagamentoParaQuitacao(formaPagamento);
        this.status = StatusLancamento.PAGO;
        this.dataPagamento = dataPagamento;
        this.atualizadoEm = Instant.now();
    }

    /** Sec6.5: PENDENTE/ATRASADO -> RECEBIDO ("confirmacao de recebimento"), so para RECEITA. `[Revisado - ADR-015]` mesma resolucao de {@link #confirmarPagamento}. */
    public void confirmarRecebimento(LocalDate dataPagamento, UUID contaBancariaId, FormaPagamento formaPagamento) {
        validarTipoParaConfirmacao(TipoLancamento.RECEITA);
        validarTransicaoParaLiquidado();
        this.contaBancariaId = resolverContaBancariaParaQuitacao(contaBancariaId);
        this.formaPagamento = resolverFormaPagamentoParaQuitacao(formaPagamento);
        this.status = StatusLancamento.RECEBIDO;
        this.dataPagamento = dataPagamento;
        this.atualizadoEm = Instant.now();
    }

    /**
     * ADR-015: se o Lancamento ja tem Conta Bancaria, mantem (ignora o que
     * veio da confirmacao); senao, exige o que veio da confirmacao.
     */
    private UUID resolverContaBancariaParaQuitacao(UUID contaBancariaIdDaConfirmacao) {
        if (this.contaBancariaId != null) {
            return this.contaBancariaId;
        }
        if (contaBancariaIdDaConfirmacao == null) {
            throw new RegraDeNegocioException("CONTA_BANCARIA_OBRIGATORIA_PARA_QUITACAO",
                    "Informe a Conta Bancaria para confirmar o pagamento ou recebimento.");
        }
        return contaBancariaIdDaConfirmacao;
    }

    /** ADR-015: mesma resolucao de {@link #resolverContaBancariaParaQuitacao}, para Forma de Pagamento. */
    private FormaPagamento resolverFormaPagamentoParaQuitacao(FormaPagamento formaPagamentoDaConfirmacao) {
        if (this.formaPagamento != null) {
            return this.formaPagamento;
        }
        if (formaPagamentoDaConfirmacao == null) {
            throw new RegraDeNegocioException("FORMA_PAGAMENTO_OBRIGATORIA_PARA_QUITACAO",
                    "Informe a Forma de Pagamento para confirmar o pagamento ou recebimento.");
        }
        return formaPagamentoDaConfirmacao;
    }

    /** Sec6.5: PENDENTE/ATRASADO -> CANCELADO. PAGO/RECEBIDO nao podem mais ser cancelados. */
    public void cancelar() {
        validarTransicaoParaLiquidado();
        this.status = StatusLancamento.CANCELADO;
        this.atualizadoEm = Instant.now();
    }

    /**
     * [Gap identificado pelo usuario - PAGO/RECEBIDO eram terminais ate
     * aqui, decisao consultada]: desfaz uma confirmacao de pagamento/
     * recebimento feita por engano. Volta para ATRASADO se a data de
     * vencimento ja passou, senao PENDENTE - mesmo criterio que
     * {@link #marcarComoAtrasado()} usaria se o lancamento nunca tivesse
     * sido confirmado. {@code dataPagamento} e limpa: o lancamento deixa de
     * ter sido pago. Uma vez reaberto, volta a ser editavel/cancelavel
     * normalmente (RN implicita: so PAGO/RECEBIDO sao terminais).
     */
    public void reabrir() {
        if (status != StatusLancamento.PAGO && status != StatusLancamento.RECEBIDO) {
            throw new RegraDeNegocioException(
                    "TRANSICAO_INVALIDA", "So e possivel reabrir um lancamento PAGO ou RECEBIDO.");
        }
        this.status = dataVencimento.isBefore(LocalDate.now()) ? StatusLancamento.ATRASADO : StatusLancamento.PENDENTE;
        this.dataPagamento = null;
        this.atualizadoEm = Instant.now();
    }

    /** Sec6.5, T1.5.4: PENDENTE -> ATRASADO quando data_vencimento &lt; hoje (job diario). */
    public void marcarComoAtrasado() {
        if (this.status != StatusLancamento.PENDENTE) {
            throw new RegraDeNegocioException(
                    "TRANSICAO_INVALIDA", "So e possivel marcar como atrasado um lancamento PENDENTE.");
        }
        this.status = StatusLancamento.ATRASADO;
        this.atualizadoEm = Instant.now();
    }

    private void validarEditavel() {
        if (!aindaNaoLiquidado()) {
            throw new RegraDeNegocioException("LANCAMENTO_NAO_EDITAVEL",
                    "Lancamento PAGO, RECEBIDO ou CANCELADO nao pode mais ser editado.");
        }
    }

    private void validarTipoParaConfirmacao(TipoLancamento... tiposPermitidos) {
        for (TipoLancamento permitido : tiposPermitidos) {
            if (permitido == this.tipo) {
                return;
            }
        }
        throw new RegraDeNegocioException("TIPO_INCOMPATIVEL_COM_CONFIRMACAO",
                "Este tipo de lancamento nao pode ser confirmado desta forma.");
    }

    private void validarTransicaoParaLiquidado() {
        if (!aindaNaoLiquidado()) {
            throw new RegraDeNegocioException("TRANSICAO_INVALIDA",
                    "So e possivel confirmar ou cancelar um lancamento PENDENTE ou ATRASADO.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getAmbienteFinanceiroId() {
        return ambienteFinanceiroId;
    }

    public TipoLancamento getTipo() {
        return tipo;
    }

    public StatusLancamento getStatus() {
        return status;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDate getDataEmissao() {
        return dataEmissao;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public LocalDate getDataPagamento() {
        return dataPagamento;
    }

    public UUID getCategoriaId() {
        return categoriaId;
    }

    public UUID getContatoId() {
        return contatoId;
    }

    public UUID getContaBancariaId() {
        return contaBancariaId;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public UUID getCartaoId() {
        return cartaoId;
    }

    public UUID getFaturaId() {
        return faturaId;
    }

    public OrigemLancamento getOrigem() {
        return origem;
    }

    public UUID getLancamentoRecorrenteId() {
        return lancamentoRecorrenteId;
    }

    public UUID getCompraId() {
        return compraId;
    }

    public UUID getVendaId() {
        return vendaId;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getObservacao() {
        return observacao;
    }

    public String getLinhaDigitavel() {
        return linhaDigitavel;
    }

    public String getIdentificadorExterno() {
        return identificadorExterno;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
