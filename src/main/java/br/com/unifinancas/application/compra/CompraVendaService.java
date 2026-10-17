package br.com.unifinancas.application.compra;

import br.com.unifinancas.application.shared.DivisorDeParcelas;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.cartao.Cartao;
import br.com.unifinancas.domain.cartao.CartaoRepository;
import br.com.unifinancas.domain.compra.Compra;
import br.com.unifinancas.domain.compra.CompraItem;
import br.com.unifinancas.domain.compra.CompraRepository;
import br.com.unifinancas.domain.contato.ContatoRepository;
import br.com.unifinancas.domain.documento.DocumentoRepository;
import br.com.unifinancas.domain.fatura.Fatura;
import br.com.unifinancas.domain.fatura.FaturaRepository;
import br.com.unifinancas.domain.fatura.StatusFatura;
import br.com.unifinancas.domain.lancamento.FormaPagamento;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.domain.venda.Venda;
import br.com.unifinancas.domain.venda.VendaItem;
import br.com.unifinancas.domain.venda.VendaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-18, SAD Sec4.8: cria uma Compra e, na mesma transacao, gera o(s)
 * Lancamento(s) DESPESA correspondentes - "como um Lancamento nasce a partir
 * de uma Compra" e responsabilidade deste servico, nunca do controller
 * (Sec9.4, tabela de padroes "Factory implicito em Application Services").
 *
 * <p>Tres caminhos de geracao, decididos com o usuario nesta tarefa (nao
 * documentados no SAD ate aqui):
 * <ul>
 *   <li>Cartao de credito (qualquer numero de parcelas, incl. 1x): distribui
 *   o valor entre as Faturas/competencias do Cartao - a 1a parcela cai na
 *   Fatura ABERTA atual, as seguintes em competencias sucessivas (Fatura
 *   criada sob demanda, {@link Fatura#novoParaCompetencia}, se ainda nao
 *   existir).</li>
 *   <li>Sem cartao, 1x (a vista): um unico Lancamento, vencimento = data da
 *   compra.</li>
 *   <li>Sem cartao, parcelado (ex.: boleto/carne fora do cartao): N
 *   Lancamentos com vencimento mensal sucessivo a partir de 1 mes apos a
 *   data da compra (mesmo criterio usado por {@code FaturaService.parcelar}
 *   para o parcelamento de Fatura em atraso).</li>
 * </ul>
 * Em todos os casos o Lancamento nasce PENDENTE (mesmo criterio de
 * {@code LancamentoFinanceiro.novoDeFatura}/{@code novoDeDocumento} - a
 * confirmacao de pagamento e sempre um passo separado e explicito).
 *
 * <p>T2.3.2, RF-19: mesma classe tambem cobre Venda (SAD Sec7.2, tabela de
 * endpoints - "CompraVendaService" ja nomeado la para os dois), bem mais
 * simples que Compra - sem cartaoId/parcelas, gera sempre um unico
 * Lancamento RECEITA ({@link #criarVenda}).
 */
@Service
public class CompraVendaService {

    private final CompraRepository compraRepository;
    private final VendaRepository vendaRepository;
    private final CartaoRepository cartaoRepository;
    private final FaturaRepository faturaRepository;
    private final ContatoRepository contatoRepository;
    private final DocumentoRepository documentoRepository;
    private final LancamentoRepository lancamentoRepository;

    public CompraVendaService(CompraRepository compraRepository, VendaRepository vendaRepository,
            CartaoRepository cartaoRepository, FaturaRepository faturaRepository, ContatoRepository contatoRepository,
            DocumentoRepository documentoRepository, LancamentoRepository lancamentoRepository) {
        this.compraRepository = compraRepository;
        this.vendaRepository = vendaRepository;
        this.cartaoRepository = cartaoRepository;
        this.faturaRepository = faturaRepository;
        this.contatoRepository = contatoRepository;
        this.documentoRepository = documentoRepository;
        this.lancamentoRepository = lancamentoRepository;
    }

    /** SAD Sec8.2: leitura de dados financeiros e permitida a qualquer perfil, inclusive VISUALIZADOR. */
    public List<CompraResumo> listar(TenantContext contexto) {
        return compraRepository.findAllByAmbienteFinanceiroId(contexto.ambienteFinanceiroId()).stream()
                .map(this::paraResumo)
                .toList();
    }

    public CompraResumo obter(TenantContext contexto, UUID id) {
        return paraResumo(buscarOuFalhar(contexto, id));
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public CompraResumo criar(TenantContext contexto, UUID contatoId, LocalDate data, FormaPagamento formaPagamento,
            UUID cartaoId, Integer parcelas, UUID documentoId, String descricao, List<ItemCompraEntrada> itensEntrada) {
        validarContato(contexto, contatoId);
        validarDocumento(contexto, documentoId);
        Cartao cartao = cartaoId != null ? buscarCartao(contexto, cartaoId) : null;

        List<CompraItem> itens = itensEntrada == null
                ? null
                : itensEntrada.stream().map(item -> CompraItem.novo(item.produtoNome(), item.quantidade(), item.valorUnitario())).toList();
        Compra compra = Compra.novo(
                contexto.ambienteFinanceiroId(), contatoId, data, formaPagamento, cartaoId, parcelas, documentoId, descricao, itens);
        compraRepository.save(compra);

        List<LancamentoFinanceiro> lancamentos = gerarLancamentos(contexto, compra, cartao);
        lancamentoRepository.saveAll(lancamentos);

        return paraResumo(compra);
    }

    /** SAD Sec8.2: leitura de dados financeiros e permitida a qualquer perfil, inclusive VISUALIZADOR. */
    public List<VendaResumo> listarVendas(TenantContext contexto) {
        return vendaRepository.findAllByAmbienteFinanceiroId(contexto.ambienteFinanceiroId()).stream()
                .map(this::paraResumo)
                .toList();
    }

    public VendaResumo obterVenda(TenantContext contexto, UUID id) {
        return paraResumo(buscarVendaOuFalhar(contexto, id));
    }

    /**
     * T2.3.2, RF-19, SAD Sec4.9: diferente de {@link #criar} (Compra), Venda
     * nao tem cartaoId/parcelas - gera sempre um unico Lancamento RECEITA,
     * com vencimento = data da venda (mesmo criterio do caminho "sem
     * cartao, a vista" de Compra).
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public VendaResumo criarVenda(TenantContext contexto, UUID contatoId, LocalDate data, FormaPagamento formaPagamento,
            UUID documentoId, String descricao, List<ItemVendaEntrada> itensEntrada) {
        validarContato(contexto, contatoId);
        validarDocumento(contexto, documentoId);

        List<VendaItem> itens = itensEntrada == null
                ? null
                : itensEntrada.stream().map(item -> VendaItem.novo(item.produtoNome(), item.quantidade(), item.valorUnitario())).toList();
        Venda venda = Venda.novo(contexto.ambienteFinanceiroId(), contatoId, data, formaPagamento, documentoId, descricao, itens);
        vendaRepository.save(venda);

        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novoDeVenda(contexto.ambienteFinanceiroId(), venda.getId(),
                venda.getContatoId(), venda.getValorTotal(), venda.getData(), venda.getData(), descricaoBase(venda));
        lancamentoRepository.save(lancamento);

        return paraResumo(venda);
    }

    /** Venda ainda nao tem descricao propria informada -> deriva do primeiro item, mesmo criterio de {@link #descricaoBase(Compra)}. */
    private String descricaoBase(Venda venda) {
        if (venda.getDescricao() != null) {
            return venda.getDescricao();
        }
        List<VendaItem> itens = venda.getItens();
        String primeiroItem = itens.get(0).getProdutoNome();
        int outrosItens = itens.size() - 1;
        if (outrosItens <= 0) {
            return primeiroItem;
        }
        return primeiroItem + " e mais " + outrosItens + (outrosItens == 1 ? " item" : " itens");
    }

    private Venda buscarVendaOuFalhar(TenantContext contexto, UUID id) {
        return vendaRepository.findByIdAndAmbienteFinanceiroId(id, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "VENDA_NAO_ENCONTRADA", "Venda nao encontrada neste Ambiente Financeiro."));
    }

    private VendaResumo paraResumo(Venda venda) {
        List<VendaItemResumo> itens = venda.getItens().stream()
                .map(item -> new VendaItemResumo(item.getId(), item.getProdutoNome(), item.getQuantidade(),
                        item.getValorUnitario(), item.getValorTotal()))
                .toList();
        return new VendaResumo(venda.getId(), venda.getContatoId(), venda.getData(), venda.getValorTotal(),
                venda.getFormaPagamento(), venda.getDocumentoId(), venda.getDescricao(), itens);
    }

    private List<LancamentoFinanceiro> gerarLancamentos(TenantContext contexto, Compra compra, Cartao cartao) {
        if (cartao != null) {
            return distribuirEmFaturas(contexto, compra, cartao);
        }
        if (compra.getParcelas() > 1) {
            return gerarParcelasSemCartao(contexto, compra);
        }
        return List.of(gerarLancamentoUnico(contexto, compra));
    }

    /** Sem cartao, 1x: vencimento = data da compra (evento ja ocorrido, sem ciclo de fatura para esperar). */
    private LancamentoFinanceiro gerarLancamentoUnico(TenantContext contexto, Compra compra) {
        return LancamentoFinanceiro.novoDeCompra(contexto.ambienteFinanceiroId(), compra.getId(), compra.getContatoId(),
                null, null, compra.getValorTotal(), compra.getData(), compra.getData(), descricaoBase(compra));
    }

    /** Sem cartao, parcelado (ex.: carne) - mesmo criterio de vencimento de {@code FaturaService.parcelar}: mensal sucessivo, aqui a partir da data da compra (que existe aqui, ao contrario do parcelamento de Fatura em atraso). */
    private List<LancamentoFinanceiro> gerarParcelasSemCartao(TenantContext contexto, Compra compra) {
        int numeroParcelas = compra.getParcelas();
        List<BigDecimal> valoresDasParcelas = DivisorDeParcelas.dividirEmParcelasIguais(compra.getValorTotal(), numeroParcelas);
        String descricaoBase = descricaoBase(compra);

        List<LancamentoFinanceiro> parcelas = new ArrayList<>();
        for (int indice = 0; indice < numeroParcelas; indice++) {
            LocalDate vencimentoParcela = compra.getData().plusMonths(indice + 1);
            String descricaoParcela = descricaoBase + " (parcela " + (indice + 1) + "/" + numeroParcelas + ")";
            parcelas.add(LancamentoFinanceiro.novoDeCompra(contexto.ambienteFinanceiroId(), compra.getId(),
                    compra.getContatoId(), null, null, valoresDasParcelas.get(indice), compra.getData(), vencimentoParcela,
                    descricaoParcela));
        }
        return parcelas;
    }

    /**
     * Cartao de credito: a 1a parcela cai na Fatura ABERTA atual do Cartao
     * (invariante: no maximo uma por vez, T2.1.2); as seguintes em
     * competencias sucessivas, cuja Fatura e criada sob demanda se ainda nao
     * existir (uma Compra parcelada em N vezes pode alcancar competencias
     * que o job diario de fechamento ainda nao criou).
     */
    private List<LancamentoFinanceiro> distribuirEmFaturas(TenantContext contexto, Compra compra, Cartao cartao) {
        int numeroParcelas = compra.getParcelas();
        List<BigDecimal> valoresDasParcelas = DivisorDeParcelas.dividirEmParcelasIguais(compra.getValorTotal(), numeroParcelas);
        String descricaoBase = descricaoBase(compra);

        Fatura faturaAberta = faturaRepository
                .findByCartaoIdAndAmbienteFinanceiroIdAndStatus(cartao.getId(), contexto.ambienteFinanceiroId(), StatusFatura.ABERTA)
                .orElseThrow(() -> new RegraDeNegocioException(
                        "FATURA_ABERTA_NAO_ENCONTRADA", "O Cartao nao possui uma Fatura ABERTA para receber esta Compra."));
        YearMonth competenciaInicial = YearMonth.from(faturaAberta.getCompetencia());

        List<LancamentoFinanceiro> parcelas = new ArrayList<>();
        List<Fatura> faturasEnvolvidas = new ArrayList<>();
        for (int indice = 0; indice < numeroParcelas; indice++) {
            Fatura fatura = indice == 0
                    ? faturaAberta
                    : buscarOuCriarFatura(contexto, cartao, competenciaInicial.plusMonths(indice));
            fatura.adicionarValor(valoresDasParcelas.get(indice));
            faturasEnvolvidas.add(fatura);

            String descricaoParcela = numeroParcelas > 1
                    ? descricaoBase + " (parcela " + (indice + 1) + "/" + numeroParcelas + ")"
                    : descricaoBase;
            parcelas.add(LancamentoFinanceiro.novoDeCompra(contexto.ambienteFinanceiroId(), compra.getId(),
                    compra.getContatoId(), cartao.getId(), fatura.getId(), valoresDasParcelas.get(indice), compra.getData(),
                    fatura.getDataVencimento(), descricaoParcela));
        }
        faturaRepository.saveAll(faturasEnvolvidas);
        return parcelas;
    }

    private Fatura buscarOuCriarFatura(TenantContext contexto, Cartao cartao, YearMonth competencia) {
        return faturaRepository
                .findByCartaoIdAndAmbienteFinanceiroIdAndCompetencia(cartao.getId(), contexto.ambienteFinanceiroId(), competencia.atDay(1))
                .orElseGet(() -> Fatura.novoParaCompetencia(cartao, competencia));
    }

    /** Compra ainda nao tem descricao propria informada -> deriva do primeiro item (decisao consultada com o usuario). */
    private String descricaoBase(Compra compra) {
        if (compra.getDescricao() != null) {
            return compra.getDescricao();
        }
        List<CompraItem> itens = compra.getItens();
        String primeiroItem = itens.get(0).getProdutoNome();
        int outrosItens = itens.size() - 1;
        if (outrosItens <= 0) {
            return primeiroItem;
        }
        return primeiroItem + " e mais " + outrosItens + (outrosItens == 1 ? " item" : " itens");
    }

    private void validarContato(TenantContext contexto, UUID contatoId) {
        if (contatoId == null) {
            return;
        }
        contatoRepository.findByIdAndAmbienteFinanceiroId(contatoId, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CONTATO_NAO_ENCONTRADO", "Contato nao encontrado neste Ambiente Financeiro."));
    }

    private void validarDocumento(TenantContext contexto, UUID documentoId) {
        if (documentoId == null) {
            return;
        }
        documentoRepository.findByIdAndAmbienteFinanceiroIdAndExcluidoEmIsNull(documentoId, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "DOCUMENTO_NAO_ENCONTRADO", "Documento nao encontrado neste Ambiente Financeiro."));
    }

    private Cartao buscarCartao(TenantContext contexto, UUID cartaoId) {
        return cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CARTAO_NAO_ENCONTRADO", "Cartao nao encontrado neste Ambiente Financeiro."));
    }

    private Compra buscarOuFalhar(TenantContext contexto, UUID id) {
        return compraRepository.findByIdAndAmbienteFinanceiroId(id, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "COMPRA_NAO_ENCONTRADA", "Compra nao encontrada neste Ambiente Financeiro."));
    }

    private CompraResumo paraResumo(Compra compra) {
        List<CompraItemResumo> itens = compra.getItens().stream()
                .map(item -> new CompraItemResumo(item.getId(), item.getProdutoNome(), item.getQuantidade(),
                        item.getValorUnitario(), item.getValorTotal()))
                .toList();
        return new CompraResumo(compra.getId(), compra.getContatoId(), compra.getData(), compra.getValorTotal(),
                compra.getFormaPagamento(), compra.getCartaoId(), compra.getParcelas(), compra.getDocumentoId(),
                compra.getDescricao(), itens);
    }
}
