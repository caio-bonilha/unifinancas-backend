package br.com.unifinancas.application.compra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.cartao.Cartao;
import br.com.unifinancas.domain.cartao.CartaoRepository;
import br.com.unifinancas.domain.compra.CompraRepository;
import br.com.unifinancas.domain.contato.ContatoRepository;
import br.com.unifinancas.domain.documento.DocumentoRepository;
import br.com.unifinancas.domain.fatura.Fatura;
import br.com.unifinancas.domain.fatura.FaturaRepository;
import br.com.unifinancas.domain.fatura.StatusFatura;
import br.com.unifinancas.domain.lancamento.FormaPagamento;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.OrigemLancamento;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.domain.venda.VendaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CompraVendaServiceTest {

    private final CompraRepository compraRepository = mock(CompraRepository.class);
    private final VendaRepository vendaRepository = mock(VendaRepository.class);
    private final CartaoRepository cartaoRepository = mock(CartaoRepository.class);
    private final FaturaRepository faturaRepository = mock(FaturaRepository.class);
    private final ContatoRepository contatoRepository = mock(ContatoRepository.class);
    private final DocumentoRepository documentoRepository = mock(DocumentoRepository.class);
    private final LancamentoRepository lancamentoRepository = mock(LancamentoRepository.class);
    private final CompraVendaService service = new CompraVendaService(compraRepository, vendaRepository, cartaoRepository,
            faturaRepository, contatoRepository, documentoRepository, lancamentoRepository);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);
    private final UUID cartaoId = UUID.randomUUID();
    private final LocalDate hoje = LocalDate.of(2026, 9, 6);

    private List<ItemCompraEntrada> doisItens() {
        return List.of(
                new ItemCompraEntrada("Arroz", new BigDecimal("2"), new BigDecimal("25.00")),
                new ItemCompraEntrada("Feijao", new BigDecimal("1"), new BigDecimal("8.50")));
    }

    private Cartao cartao() {
        Cartao cartao = Cartao.novo(ambienteFinanceiroId, null, "Nubank", new BigDecimal("5000.00"), 20, 27, null);
        ReflectionTestUtils.setField(cartao, "id", cartaoId);
        return cartao;
    }

    private Fatura faturaAberta(Cartao cartao, YearMonth competencia) {
        Fatura fatura = Fatura.novoParaCompetencia(cartao, competencia);
        ReflectionTestUtils.setField(fatura, "id", UUID.randomUUID());
        return fatura;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<LancamentoFinanceiro>> capturarLancamentosSalvos() {
        ArgumentCaptor<List<LancamentoFinanceiro>> capturado = ArgumentCaptor.forClass(List.class);
        verify(lancamentoRepository).saveAll(capturado.capture());
        return capturado;
    }

    @Test
    void compraAVistaSemCartaoGeraUmUnicoLancamentoComVencimentoNaDataDaCompra() {
        CompraResumo resumo = service.criar(contexto, null, hoje, FormaPagamento.PIX, null, null, null, null, doisItens());

        assertThat(resumo.valorTotal()).isEqualByComparingTo("58.50");
        List<LancamentoFinanceiro> lancamentos = capturarLancamentosSalvos().getValue();
        assertThat(lancamentos).hasSize(1);
        LancamentoFinanceiro lancamento = lancamentos.get(0);
        assertThat(lancamento.getValor()).isEqualByComparingTo("58.50");
        assertThat(lancamento.getDataVencimento()).isEqualTo(hoje);
        assertThat(lancamento.getOrigem()).isEqualTo(OrigemLancamento.COMPRA);
        assertThat(lancamento.getCartaoId()).isNull();
        assertThat(lancamento.getFaturaId()).isNull();
        assertThat(lancamento.getDescricao()).isEqualTo("Arroz e mais 1 item");
    }

    @Test
    void descricaoInformadaEUsadaSemSufixoQuandoUmaSoParcela() {
        service.criar(contexto, null, hoje, FormaPagamento.DINHEIRO, null, null, null, "Feira da semana", doisItens());

        LancamentoFinanceiro lancamento = capturarLancamentosSalvos().getValue().get(0);
        assertThat(lancamento.getDescricao()).isEqualTo("Feira da semana");
    }

    @Test
    void compraParceladaSemCartaoGeraNLancamentosComVencimentoMensalSucessivo() {
        service.criar(contexto, null, hoje, FormaPagamento.BOLETO, null, 3, null, "Carne da loja", doisItens());

        List<LancamentoFinanceiro> parcelas = capturarLancamentosSalvos().getValue();
        assertThat(parcelas).hasSize(3);
        assertThat(parcelas.get(0).getDataVencimento()).isEqualTo(hoje.plusMonths(1));
        assertThat(parcelas.get(1).getDataVencimento()).isEqualTo(hoje.plusMonths(2));
        assertThat(parcelas.get(2).getDataVencimento()).isEqualTo(hoje.plusMonths(3));
        assertThat(parcelas).allSatisfy(parcela -> {
            assertThat(parcela.getCartaoId()).isNull();
            assertThat(parcela.getFaturaId()).isNull();
        });
        assertThat(parcelas.get(0).getDescricao()).isEqualTo("Carne da loja (parcela 1/3)");

        BigDecimal soma = parcelas.stream().map(LancamentoFinanceiro::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(soma).isEqualByComparingTo("58.50");
    }

    @Test
    void compraNoCartaoAVistaCaiNaFaturaAbertaAtual() {
        Cartao cartao = cartao();
        Fatura faturaAberta = faturaAberta(cartao, YearMonth.from(hoje));
        when(cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, ambienteFinanceiroId)).thenReturn(Optional.of(cartao));
        when(faturaRepository.findByCartaoIdAndAmbienteFinanceiroIdAndStatus(cartaoId, ambienteFinanceiroId, StatusFatura.ABERTA))
                .thenReturn(Optional.of(faturaAberta));

        service.criar(contexto, null, hoje, FormaPagamento.CARTAO_CREDITO, cartaoId, null, null, null, doisItens());

        assertThat(faturaAberta.getValorTotal()).isEqualByComparingTo("58.50");
        List<LancamentoFinanceiro> lancamentos = capturarLancamentosSalvos().getValue();
        assertThat(lancamentos).hasSize(1);
        assertThat(lancamentos.get(0).getCartaoId()).isEqualTo(cartaoId);
        assertThat(lancamentos.get(0).getFaturaId()).isEqualTo(faturaAberta.getId());
        assertThat(lancamentos.get(0).getDataVencimento()).isEqualTo(faturaAberta.getDataVencimento());
    }

    @Test
    void compraNoCartaoParceladaDistribuiEntreFaturasCriandoAsFuturasSobDemanda() {
        Cartao cartao = cartao();
        YearMonth competenciaAtual = YearMonth.from(hoje);
        Fatura faturaAberta = faturaAberta(cartao, competenciaAtual);
        when(cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, ambienteFinanceiroId)).thenReturn(Optional.of(cartao));
        when(faturaRepository.findByCartaoIdAndAmbienteFinanceiroIdAndStatus(cartaoId, ambienteFinanceiroId, StatusFatura.ABERTA))
                .thenReturn(Optional.of(faturaAberta));
        when(faturaRepository.findByCartaoIdAndAmbienteFinanceiroIdAndCompetencia(any(), any(), any())).thenReturn(Optional.empty());

        service.criar(contexto, null, hoje, FormaPagamento.CARTAO_CREDITO, cartaoId, 3, null, "Compra parcelada", doisItens());

        List<LancamentoFinanceiro> parcelas = capturarLancamentosSalvos().getValue();
        assertThat(parcelas).hasSize(3);
        assertThat(parcelas.get(0).getFaturaId()).isEqualTo(faturaAberta.getId());
        assertThat(parcelas).allSatisfy(parcela -> assertThat(parcela.getCartaoId()).isEqualTo(cartaoId));
        assertThat(parcelas.get(0).getDescricao()).isEqualTo("Compra parcelada (parcela 1/3)");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Fatura>> faturasCapturadas = ArgumentCaptor.forClass(List.class);
        verify(faturaRepository).saveAll(faturasCapturadas.capture());
        List<Fatura> faturasEnvolvidas = faturasCapturadas.getValue();
        assertThat(faturasEnvolvidas).hasSize(3);
        assertThat(faturasEnvolvidas.stream().map(Fatura::getCompetencia))
                .containsExactly(competenciaAtual.atDay(1), competenciaAtual.plusMonths(1).atDay(1), competenciaAtual.plusMonths(2).atDay(1));

        BigDecimal somaDasParcelas = parcelas.stream().map(LancamentoFinanceiro::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somaDasParcelas).isEqualByComparingTo("58.50");
    }

    @Test
    void compraNoCartaoSemFaturaAbertaLancaRegraDeNegocio() {
        Cartao cartao = cartao();
        when(cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, ambienteFinanceiroId)).thenReturn(Optional.of(cartao));
        when(faturaRepository.findByCartaoIdAndAmbienteFinanceiroIdAndStatus(cartaoId, ambienteFinanceiroId, StatusFatura.ABERTA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(
                        contexto, null, hoje, FormaPagamento.CARTAO_CREDITO, cartaoId, null, null, null, doisItens()))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("FATURA_ABERTA_NAO_ENCONTRADA"));
    }

    @Test
    void contatoDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID contatoId = UUID.randomUUID();
        when(contatoRepository.findByIdAndAmbienteFinanceiroId(contatoId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(contexto, contatoId, hoje, FormaPagamento.PIX, null, null, null, null, doisItens()))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CONTATO_NAO_ENCONTRADO"));
    }

    @Test
    void documentoDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID documentoId = UUID.randomUUID();
        when(documentoRepository.findByIdAndAmbienteFinanceiroIdAndExcluidoEmIsNull(documentoId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(
                        contexto, null, hoje, FormaPagamento.PIX, null, null, documentoId, null, doisItens()))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("DOCUMENTO_NAO_ENCONTRADO"));
    }

    @Test
    void cartaoDeOutroAmbienteLancaRecursoNaoEncontrado() {
        when(cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(
                        contexto, null, hoje, FormaPagamento.CARTAO_CREDITO, cartaoId, null, null, null, doisItens()))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CARTAO_NAO_ENCONTRADO"));
    }

    @Test
    void obterUmaCompraQueNaoExisteLancaRecursoNaoEncontrado() {
        UUID compraId = UUID.randomUUID();
        when(compraRepository.findByIdAndAmbienteFinanceiroId(compraId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obter(contexto, compraId))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("COMPRA_NAO_ENCONTRADA"));
    }

    private List<ItemVendaEntrada> doisItensVenda() {
        return List.of(
                new ItemVendaEntrada("Bolo de fuba", new BigDecimal("2"), new BigDecimal("15.00")),
                new ItemVendaEntrada("Suco natural", new BigDecimal("1"), new BigDecimal("8.00")));
    }

    @Test
    void criarVendaGeraUmUnicoLancamentoReceitaComVencimentoNaDataDaVenda() {
        VendaResumo resumo = service.criarVenda(contexto, null, hoje, FormaPagamento.PIX, null, null, doisItensVenda());

        assertThat(resumo.valorTotal()).isEqualByComparingTo("38.00");
        ArgumentCaptor<LancamentoFinanceiro> capturado = ArgumentCaptor.forClass(LancamentoFinanceiro.class);
        verify(lancamentoRepository).save(capturado.capture());
        LancamentoFinanceiro lancamento = capturado.getValue();
        assertThat(lancamento.getTipo().name()).isEqualTo("RECEITA");
        assertThat(lancamento.getValor()).isEqualByComparingTo("38.00");
        assertThat(lancamento.getDataVencimento()).isEqualTo(hoje);
        assertThat(lancamento.getOrigem()).isEqualTo(OrigemLancamento.VENDA);
        assertThat(lancamento.getDescricao()).isEqualTo("Bolo de fuba e mais 1 item");
    }

    @Test
    void descricaoDaVendaInformadaEUsadaSemDerivar() {
        service.criarVenda(contexto, null, hoje, FormaPagamento.DINHEIRO, null, "Feira de sabado", doisItensVenda());

        ArgumentCaptor<LancamentoFinanceiro> capturado = ArgumentCaptor.forClass(LancamentoFinanceiro.class);
        verify(lancamentoRepository).save(capturado.capture());
        assertThat(capturado.getValue().getDescricao()).isEqualTo("Feira de sabado");
    }

    @Test
    void contatoDaVendaDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID contatoId = UUID.randomUUID();
        when(contatoRepository.findByIdAndAmbienteFinanceiroId(contatoId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarVenda(contexto, contatoId, hoje, FormaPagamento.PIX, null, null, doisItensVenda()))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CONTATO_NAO_ENCONTRADO"));
    }

    @Test
    void obterUmaVendaQueNaoExisteLancaRecursoNaoEncontrado() {
        UUID vendaId = UUID.randomUUID();
        when(vendaRepository.findByIdAndAmbienteFinanceiroId(vendaId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obterVenda(contexto, vendaId))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("VENDA_NAO_ENCONTRADA"));
    }
}
