package br.com.unifinancas.application.lancamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.application.contabancaria.ContaBancariaService;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.categoria.Categoria;
import br.com.unifinancas.domain.categoria.CategoriaRepository;
import br.com.unifinancas.domain.categoria.TipoCategoria;
import br.com.unifinancas.domain.contabancaria.ContaBancaria;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.contabancaria.TipoContaBancaria;
import br.com.unifinancas.domain.contato.ContatoRepository;
import br.com.unifinancas.domain.lancamento.FormaPagamento;
import br.com.unifinancas.domain.lancamento.LancamentoCriadoEvent;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.OrigemLancamento;
import br.com.unifinancas.domain.lancamento.StatusLancamento;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.movimentacao.Movimentacao;
import br.com.unifinancas.domain.movimentacao.MovimentacaoRepository;
import br.com.unifinancas.domain.movimentacao.TipoMovimentacao;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class LancamentoServiceTest {

    private final LancamentoRepository lancamentoRepository = mock(LancamentoRepository.class);
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final ContatoRepository contatoRepository = mock(ContatoRepository.class);
    private final ContaBancariaRepository contaBancariaRepository = mock(ContaBancariaRepository.class);
    private final ContaBancariaService contaBancariaService = mock(ContaBancariaService.class);
    private final MovimentacaoRepository movimentacaoRepository = mock(MovimentacaoRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final LancamentoService service = new LancamentoService(lancamentoRepository, categoriaRepository,
            contatoRepository, contaBancariaRepository, contaBancariaService, movimentacaoRepository, eventPublisher);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);
    private final UUID contaBancariaId = UUID.randomUUID();
    private final LocalDate hoje = LocalDate.now();

    @Test
    void criarPersisteOLancamentoNoAmbienteDoContexto() {
        LancamentoResumo resumo = service.criar(contexto, TipoLancamento.DESPESA, new BigDecimal("100.00"), hoje,
                hoje.plusDays(3), null, null, null, null, "Descrição de teste", "aluguel");

        ArgumentCaptor<LancamentoFinanceiro> capturado = ArgumentCaptor.forClass(LancamentoFinanceiro.class);
        verify(lancamentoRepository).save(capturado.capture());
        assertThat(capturado.getValue().getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(resumo.valor()).isEqualByComparingTo("100.00");
        assertThat(resumo.status()).isEqualTo(StatusLancamento.PENDENTE);
    }

    @Test
    void criarPublicaLancamentoCriadoEventComOsDadosCorretos() {
        UUID categoriaId = UUID.randomUUID();
        Categoria categoria = Categoria.novo(ambienteFinanceiroId, "Aluguel", TipoCategoria.DESPESA, null);
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.of(categoria));

        LancamentoResumo resumo = service.criar(contexto, TipoLancamento.DESPESA, new BigDecimal("100.00"), hoje,
                hoje.plusDays(3), categoriaId, null, null, null, "Descrição de teste", "aluguel");

        ArgumentCaptor<LancamentoCriadoEvent> evento = ArgumentCaptor.forClass(LancamentoCriadoEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().lancamentoId()).isEqualTo(resumo.id());
        assertThat(evento.getValue().ambienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(evento.getValue().categoriaId()).isEqualTo(categoriaId);
        assertThat(evento.getValue().usuarioId()).isEqualTo(usuarioId);
        assertThat(evento.getValue().tipo()).isEqualTo(TipoLancamento.DESPESA);
        assertThat(evento.getValue().valor()).isEqualByComparingTo("100.00");
        assertThat(evento.getValue().ocorridoEm()).isNotNull();
    }

    @Test
    void criarComCategoriaDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID categoriaId = UUID.randomUUID();
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(contexto, TipoLancamento.DESPESA, new BigDecimal("100.00"), hoje, hoje,
                categoriaId, null, null, null, "Descrição de teste", null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CATEGORIA_NAO_ENCONTRADA"));
    }

    @Test
    void criarComContaBancariaDeOutroAmbienteLancaRecursoNaoEncontrado() {
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(contexto, TipoLancamento.DESPESA, new BigDecimal("100.00"), hoje, hoje,
                null, null, contaBancariaId, null, "Descrição de teste", null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CONTA_BANCARIA_NAO_ENCONTRADA"));
    }

    @Test
    void criarComReferenciasValidasFunciona() {
        UUID categoriaId = UUID.randomUUID();
        Categoria categoria = Categoria.novo(ambienteFinanceiroId, "Aluguel", TipoCategoria.DESPESA, null);
        ContaBancaria conta = ContaBancaria.novo(ambienteFinanceiroId, "Conta", TipoContaBancaria.CORRENTE, BigDecimal.ZERO);
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.of(categoria));
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteFinanceiroId)).thenReturn(Optional.of(conta));

        LancamentoResumo resumo = service.criar(contexto, TipoLancamento.DESPESA, new BigDecimal("100.00"), hoje, hoje,
                categoriaId, null, contaBancariaId, null, "Descrição de teste", null);

        assertThat(resumo.categoriaId()).isEqualTo(categoriaId);
        assertThat(resumo.contaBancariaId()).isEqualTo(contaBancariaId);
    }

    @Test
    void criarDeRecorrenciaPersisteComOrigemRecorrenciaEPublicaEvento() {
        UUID lancamentoRecorrenteId = UUID.randomUUID();

        LancamentoResumo resumo = service.criarDeRecorrencia(ambienteFinanceiroId, lancamentoRecorrenteId,
                TipoLancamento.DESPESA, new BigDecimal("1500.00"), hoje, hoje, null, contaBancariaId, "Descrição de teste",
                "Gerado automaticamente pela recorrencia: Aluguel");

        assertThat(resumo.origem()).isEqualTo(OrigemLancamento.RECORRENCIA);
        ArgumentCaptor<LancamentoCriadoEvent> evento = ArgumentCaptor.forClass(LancamentoCriadoEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().usuarioId()).isNull();
        assertThat(evento.getValue().ambienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
    }

    @Test
    void criarDeDocumentoPersisteComOrigemDocumentoEPublicaEvento() {
        LancamentoResumo resumo = service.criarDeDocumento(contexto, TipoLancamento.DESPESA, new BigDecimal("260.35"),
                hoje, hoje.plusDays(10), null, null, null, null, "Conta de agua", null);

        assertThat(resumo.origem()).isEqualTo(OrigemLancamento.DOCUMENTO);
        ArgumentCaptor<LancamentoFinanceiro> capturado = ArgumentCaptor.forClass(LancamentoFinanceiro.class);
        verify(lancamentoRepository).save(capturado.capture());
        // `[Revisado - Etapa B]` o vinculo com o Documento agora mora em
        // documento.lancamento_id (N:1), gravado pelo ProcessamentoDocumentoService.
        assertThat(capturado.getValue().getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);

        ArgumentCaptor<LancamentoCriadoEvent> evento = ArgumentCaptor.forClass(LancamentoCriadoEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().usuarioId()).isEqualTo(usuarioId);
        assertThat(evento.getValue().valor()).isEqualByComparingTo("260.35");
    }

    @Test
    void criarDeDocumentoComCategoriaDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID categoriaId = UUID.randomUUID();
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarDeDocumento(contexto, TipoLancamento.DESPESA, new BigDecimal("10.00"),
                hoje, hoje, categoriaId, null, null, null, "Descrição de teste", null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void criarDeImportacaoNasceJaPagoParaDespesaECriaMovimentacaoEEventoDeDominio() {
        ContaBancaria conta = ContaBancaria.novo(ambienteFinanceiroId, "Conta", TipoContaBancaria.CORRENTE, BigDecimal.ZERO);
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteFinanceiroId)).thenReturn(Optional.of(conta));

        LancamentoResumo resumo = service.criarDeImportacao(contexto, TipoLancamento.DESPESA, new BigDecimal("45.90"),
                hoje, null, contaBancariaId, "Padaria", "FITID-1");

        assertThat(resumo.status()).isEqualTo(StatusLancamento.PAGO);
        assertThat(resumo.origem()).isEqualTo(OrigemLancamento.IMPORTACAO);
        assertThat(resumo.dataPagamento()).isEqualTo(hoje);
        assertThat(resumo.contaBancariaId()).isEqualTo(contaBancariaId);
        assertThat(resumo.formaPagamento()).isEqualTo(FormaPagamento.TRANSFERENCIA);

        ArgumentCaptor<LancamentoCriadoEvent> evento = ArgumentCaptor.forClass(LancamentoCriadoEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().valor()).isEqualByComparingTo("45.90");

        ArgumentCaptor<Movimentacao> movimentacao = ArgumentCaptor.forClass(Movimentacao.class);
        verify(movimentacaoRepository).save(movimentacao.capture());
        assertThat(movimentacao.getValue().getTipo()).isEqualTo(TipoMovimentacao.DEBITO);
        verify(contaBancariaService).recalcularSaldo(ambienteFinanceiroId, contaBancariaId);
    }

    @Test
    void criarDeImportacaoNasceJaRecebidoParaReceita() {
        ContaBancaria conta = ContaBancaria.novo(ambienteFinanceiroId, "Conta", TipoContaBancaria.CORRENTE, BigDecimal.ZERO);
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteFinanceiroId)).thenReturn(Optional.of(conta));

        LancamentoResumo resumo = service.criarDeImportacao(contexto, TipoLancamento.RECEITA, new BigDecimal("1500.00"),
                hoje, null, contaBancariaId, "Salario", "FITID-2");

        assertThat(resumo.status()).isEqualTo(StatusLancamento.RECEBIDO);
        ArgumentCaptor<Movimentacao> movimentacao = ArgumentCaptor.forClass(Movimentacao.class);
        verify(movimentacaoRepository).save(movimentacao.capture());
        assertThat(movimentacao.getValue().getTipo()).isEqualTo(TipoMovimentacao.CREDITO);
    }

    @Test
    void criarDeImportacaoComContaBancariaDeOutroAmbienteLancaRecursoNaoEncontrado() {
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarDeImportacao(contexto, TipoLancamento.DESPESA, new BigDecimal("45.90"),
                hoje, null, contaBancariaId, "Padaria", "FITID-3"))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CONTA_BANCARIA_NAO_ENCONTRADA"));
    }

    @Test
    void editarUmLancamentoInexistenteLancaRecursoNaoEncontrado() {
        UUID lancamentoId = UUID.randomUUID();
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editar(contexto, lancamentoId, new BigDecimal("10.00"), hoje, hoje, null,
                null, null, null, "Descrição de teste", null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("LANCAMENTO_NAO_ENCONTRADO"));
    }

    @Test
    void editarUmLancamentoExistenteAtualizaOsCampos() {
        UUID lancamentoId = UUID.randomUUID();
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, null, null, "Descrição de teste", null);
        ReflectionTestUtils.setField(lancamento, "id", lancamentoId);
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamento));

        LancamentoResumo resumo = service.editar(contexto, lancamentoId, new BigDecimal("80.00"), hoje, hoje.plusDays(2),
                null, null, null, null, "Descrição de teste", "atualizado");

        assertThat(resumo.valor()).isEqualByComparingTo("80.00");
        assertThat(resumo.observacao()).isEqualTo("atualizado");
        verify(lancamentoRepository).save(lancamento);
    }

    @Test
    void confirmarPagamentoMudaOStatusParaPago() {
        UUID lancamentoId = UUID.randomUUID();
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, null, null, "Descrição de teste", null);
        ReflectionTestUtils.setField(lancamento, "id", lancamentoId);
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamento));

        LancamentoResumo resumo = service.confirmarPagamento(contexto, lancamentoId, hoje, contaBancariaId, FormaPagamento.PIX);

        assertThat(resumo.status()).isEqualTo(StatusLancamento.PAGO);
        assertThat(resumo.dataPagamento()).isEqualTo(hoje);
    }

    @Test
    void confirmarRecebimentoMudaOStatusParaRecebido() {
        UUID lancamentoId = UUID.randomUUID();
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.RECEITA,
                new BigDecimal("50.00"), hoje, hoje, null, null, null, null, "Descrição de teste", null);
        ReflectionTestUtils.setField(lancamento, "id", lancamentoId);
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamento));

        LancamentoResumo resumo = service.confirmarRecebimento(contexto, lancamentoId, hoje, contaBancariaId, FormaPagamento.PIX);

        assertThat(resumo.status()).isEqualTo(StatusLancamento.RECEBIDO);
    }

    /** ADR-015: sem Conta Bancaria no Lancamento nem na confirmacao, exige - nao chega a recalcular saldo nem criar Movimentacao. */
    @Test
    void confirmarPagamentoSemContaBancariaLancaRegraDeNegocioENaoRecalculaSaldoNemCriaMovimentacao() {
        UUID lancamentoId = UUID.randomUUID();
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, null, null, "Descrição de teste", null);
        ReflectionTestUtils.setField(lancamento, "id", lancamentoId);
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamento));

        assertThatThrownBy(() -> service.confirmarPagamento(contexto, lancamentoId, hoje, null, FormaPagamento.PIX))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("CONTA_BANCARIA_OBRIGATORIA_PARA_QUITACAO"));
        org.mockito.Mockito.verifyNoInteractions(contaBancariaService);
        org.mockito.Mockito.verifyNoInteractions(movimentacaoRepository);
    }

    @Test
    void confirmarPagamentoDeDespesaComContaBancariaCriaMovimentacaoDebitoERecalculaOSaldo() {
        UUID lancamentoId = UUID.randomUUID();
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);
        ReflectionTestUtils.setField(lancamento, "id", lancamentoId);
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamento));

        service.confirmarPagamento(contexto, lancamentoId, hoje, null, FormaPagamento.PIX);

        ArgumentCaptor<Movimentacao> capturada = ArgumentCaptor.forClass(Movimentacao.class);
        verify(movimentacaoRepository).save(capturada.capture());
        assertThat(capturada.getValue().getTipo()).isEqualTo(TipoMovimentacao.DEBITO);
        assertThat(capturada.getValue().getContaBancariaId()).isEqualTo(contaBancariaId);
        assertThat(capturada.getValue().getLancamentoId()).isEqualTo(lancamentoId);
        assertThat(capturada.getValue().getValor()).isEqualByComparingTo("50.00");
        verify(contaBancariaService).recalcularSaldo(ambienteFinanceiroId, contaBancariaId);
    }

    @Test
    void confirmarRecebimentoComContaBancariaCriaMovimentacaoCreditoERecalculaOSaldo() {
        UUID lancamentoId = UUID.randomUUID();
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.RECEITA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);
        ReflectionTestUtils.setField(lancamento, "id", lancamentoId);
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamento));

        service.confirmarRecebimento(contexto, lancamentoId, hoje, null, FormaPagamento.PIX);

        ArgumentCaptor<Movimentacao> capturada = ArgumentCaptor.forClass(Movimentacao.class);
        verify(movimentacaoRepository).save(capturada.capture());
        assertThat(capturada.getValue().getTipo()).isEqualTo(TipoMovimentacao.CREDITO);
        verify(contaBancariaService).recalcularSaldo(ambienteFinanceiroId, contaBancariaId);
    }

    /** ADR-015: Lancamento sem Conta/Forma - a confirmacao fornece os dois e eles sao gravados nele. */
    @Test
    void confirmarPagamentoPreenchendoContaEFormaViaConfirmacaoGravaOsCamposNoLancamento() {
        UUID lancamentoId = UUID.randomUUID();
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, null, null, "Descrição de teste", null);
        ReflectionTestUtils.setField(lancamento, "id", lancamentoId);
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamento));

        LancamentoResumo resumo = service.confirmarPagamento(contexto, lancamentoId, hoje, contaBancariaId, FormaPagamento.PIX);

        assertThat(resumo.contaBancariaId()).isEqualTo(contaBancariaId);
        assertThat(resumo.formaPagamento()).isEqualTo(FormaPagamento.PIX);
    }

    @Test
    void reabrirUmaDespesaPagaComContaBancariaRemoveAMovimentacaoERecalculaOSaldo() {
        UUID lancamentoId = UUID.randomUUID();
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje.plusDays(3), null, null, contaBancariaId, null, "Descrição de teste", null);
        lancamento.confirmarPagamento(hoje, contaBancariaId, FormaPagamento.PIX);
        ReflectionTestUtils.setField(lancamento, "id", lancamentoId);
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamento));
        Movimentacao movimentacao = Movimentacao.paraLancamento(ambienteFinanceiroId, contaBancariaId,
                TipoMovimentacao.DEBITO, new BigDecimal("50.00"), hoje, "Descrição de teste", lancamentoId);
        when(movimentacaoRepository.findByAmbienteFinanceiroIdAndLancamentoId(ambienteFinanceiroId, lancamentoId))
                .thenReturn(Optional.of(movimentacao));

        LancamentoResumo resumo = service.reabrir(contexto, lancamentoId);

        assertThat(resumo.status()).isEqualTo(StatusLancamento.PENDENTE);
        assertThat(resumo.dataPagamento()).isNull();
        verify(movimentacaoRepository).delete(movimentacao);
        verify(contaBancariaService).recalcularSaldo(ambienteFinanceiroId, contaBancariaId);
    }

    @Test
    void cancelarMudaOStatusParaCancelado() {
        UUID lancamentoId = UUID.randomUUID();
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, null, null, "Descrição de teste", null);
        ReflectionTestUtils.setField(lancamento, "id", lancamentoId);
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamento));

        LancamentoResumo resumo = service.cancelar(contexto, lancamentoId);

        assertThat(resumo.status()).isEqualTo(StatusLancamento.CANCELADO);
    }

    @Test
    void cancelarUmLancamentoInexistenteLancaRecursoNaoEncontrado() {
        UUID lancamentoId = UUID.randomUUID();
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelar(contexto, lancamentoId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void listarSemProximaPaginaNaoRetornaCursor() {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("10.00"), hoje, hoje, null, null, null, null, "Descrição de teste", null);
        when(lancamentoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(paginaCom(List.of(lancamento)));

        PaginaLancamentos pagina = service.listar(contexto, null, null, null, null, null, null);

        assertThat(pagina.itens()).hasSize(1);
        assertThat(pagina.proximoCursor()).isNull();
    }

    @Test
    void listarComMaisResultadosQueOLimiteRetornaProximoCursorDecodavel() {
        LancamentoFinanceiro um = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("10.00"), hoje, hoje, null, null, null, null, "Descrição de teste", null);
        ReflectionTestUtils.setField(um, "id", UUID.randomUUID());
        LancamentoFinanceiro dois = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("20.00"), hoje, hoje.plusDays(1), null, null, null, null, "Descrição de teste", null);
        ReflectionTestUtils.setField(dois, "id", UUID.randomUUID());
        // service pede limite+1 para saber se ha proxima pagina - simulamos limit=1 pedindo 2 e devolvendo os 2
        when(lancamentoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(paginaCom(List.of(um, dois)));

        PaginaLancamentos pagina = service.listar(contexto, null, null, null, null, null, 1);

        assertThat(pagina.itens()).hasSize(1);
        assertThat(pagina.itens().get(0).valor()).isEqualByComparingTo("10.00");
        assertThat(pagina.proximoCursor()).isNotNull();
        assertThat(CursorLancamento.decodificar(pagina.proximoCursor())).isEqualTo(new CursorLancamento(hoje, um.getId()));
    }

    @Test
    void listarComLimiteForaDoIntervaloLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.listar(contexto, null, null, null, null, null, 0))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("LIMITE_INVALIDO"));

        assertThatThrownBy(() -> service.listar(contexto, null, null, null, null, null, 101))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("LIMITE_INVALIDO"));
    }

    @Test
    void listarComCursorInvalidoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.listar(contexto, null, null, null, null, "cursor-invalido-!!", null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("CURSOR_INVALIDO"));
    }

    private Page<LancamentoFinanceiro> paginaCom(List<LancamentoFinanceiro> itens) {
        return new PageImpl<>(itens);
    }

    @Test
    void editarConfirmarECancelarNuncaPublicamLancamentoCriadoEvent() {
        UUID lancamentoId = UUID.randomUUID();
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, null, null, "Descrição de teste", null);
        ReflectionTestUtils.setField(lancamento, "id", lancamentoId);
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamento));

        service.editar(contexto, lancamentoId, new BigDecimal("60.00"), hoje, hoje, null, null, null, null, "Descrição de teste", null);
        service.confirmarPagamento(contexto, lancamentoId, hoje, contaBancariaId, FormaPagamento.PIX);

        org.mockito.Mockito.verifyNoInteractions(eventPublisher);
    }

    /** ADR-015, T1.3c.4: quitacao em lote. */
    @Test
    void quitarEmLoteConfirmaTodosOsLancamentosDoMesmoTipo() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        LancamentoFinanceiro despesa1 = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, null, null, "Aluguel", null);
        ReflectionTestUtils.setField(despesa1, "id", id1);
        LancamentoFinanceiro despesa2 = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("30.00"), hoje, hoje, null, null, null, null, "Água", null);
        ReflectionTestUtils.setField(despesa2, "id", id2);
        when(lancamentoRepository.findAllByIdInAndAmbienteFinanceiroId(List.of(id1, id2), ambienteFinanceiroId))
                .thenReturn(List.of(despesa1, despesa2));

        List<LancamentoResumo> resumos = service.quitarEmLote(contexto, List.of(id1, id2), hoje, contaBancariaId, FormaPagamento.PIX);

        assertThat(resumos).hasSize(2);
        assertThat(resumos).allSatisfy(resumo -> assertThat(resumo.status()).isEqualTo(StatusLancamento.PAGO));
        verify(contaBancariaService, org.mockito.Mockito.times(2)).recalcularSaldo(ambienteFinanceiroId, contaBancariaId);
    }

    /** ADR-015: item que ja tem Conta/Forma preenchidas mantem o que ja tinha - o fallback do lote so vale para os sem. */
    @Test
    void quitarEmLoteMantemContaEFormaDeQuemJaTinhaEUsaOFallbackSoNosQueNaoTinham() {
        UUID idComConta = UUID.randomUUID();
        UUID idSemConta = UUID.randomUUID();
        UUID outraContaBancariaId = UUID.randomUUID();
        LancamentoFinanceiro comConta = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, outraContaBancariaId, FormaPagamento.BOLETO, "Aluguel", null);
        ReflectionTestUtils.setField(comConta, "id", idComConta);
        LancamentoFinanceiro semConta = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("30.00"), hoje, hoje, null, null, null, null, "Água", null);
        ReflectionTestUtils.setField(semConta, "id", idSemConta);
        when(lancamentoRepository.findAllByIdInAndAmbienteFinanceiroId(List.of(idComConta, idSemConta), ambienteFinanceiroId))
                .thenReturn(List.of(comConta, semConta));

        service.quitarEmLote(contexto, List.of(idComConta, idSemConta), hoje, contaBancariaId, FormaPagamento.PIX);

        assertThat(comConta.getContaBancariaId()).isEqualTo(outraContaBancariaId);
        assertThat(comConta.getFormaPagamento()).isEqualTo(FormaPagamento.BOLETO);
        assertThat(semConta.getContaBancariaId()).isEqualTo(contaBancariaId);
        assertThat(semConta.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
    }

    @Test
    void quitarEmLoteComTiposMistosLancaRegraDeNegocio() {
        UUID idDespesa = UUID.randomUUID();
        UUID idReceita = UUID.randomUUID();
        LancamentoFinanceiro despesa = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, FormaPagamento.PIX, "Aluguel", null);
        ReflectionTestUtils.setField(despesa, "id", idDespesa);
        LancamentoFinanceiro receita = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.RECEITA,
                new BigDecimal("30.00"), hoje, hoje, null, null, contaBancariaId, FormaPagamento.PIX, "Salário", null);
        ReflectionTestUtils.setField(receita, "id", idReceita);
        when(lancamentoRepository.findAllByIdInAndAmbienteFinanceiroId(List.of(idDespesa, idReceita), ambienteFinanceiroId))
                .thenReturn(List.of(despesa, receita));

        assertThatThrownBy(() -> service.quitarEmLote(contexto, List.of(idDespesa, idReceita), hoje, contaBancariaId, FormaPagamento.PIX))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("LOTE_COM_TIPOS_MISTOS"));
    }

    @Test
    void quitarEmLoteComIdDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID idExistente = UUID.randomUUID();
        UUID idInexistente = UUID.randomUUID();
        LancamentoFinanceiro despesa = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, FormaPagamento.PIX, "Aluguel", null);
        ReflectionTestUtils.setField(despesa, "id", idExistente);
        when(lancamentoRepository.findAllByIdInAndAmbienteFinanceiroId(List.of(idExistente, idInexistente), ambienteFinanceiroId))
                .thenReturn(List.of(despesa));

        assertThatThrownBy(() -> service.quitarEmLote(contexto, List.of(idExistente, idInexistente), hoje, contaBancariaId, FormaPagamento.PIX))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("LANCAMENTO_NAO_ENCONTRADO"));
    }
}
