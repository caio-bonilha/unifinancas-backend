package br.com.unifinancas.application.movimentacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.application.contabancaria.ContaBancariaService;
import br.com.unifinancas.application.lancamento.LancamentoResumo;
import br.com.unifinancas.application.lancamento.LancamentoService;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.categoria.Categoria;
import br.com.unifinancas.domain.categoria.CategoriaRepository;
import br.com.unifinancas.domain.categoria.TipoCategoria;
import br.com.unifinancas.domain.contabancaria.ContaBancaria;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.contabancaria.TipoContaBancaria;
import br.com.unifinancas.domain.movimentacao.Movimentacao;
import br.com.unifinancas.domain.movimentacao.MovimentacaoRepository;
import br.com.unifinancas.domain.movimentacao.OrigemMovimentacao;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class MovimentacaoServiceTest {

    private final MovimentacaoRepository movimentacaoRepository = mock(MovimentacaoRepository.class);
    private final ContaBancariaRepository contaBancariaRepository = mock(ContaBancariaRepository.class);
    private final ContaBancariaService contaBancariaService = mock(ContaBancariaService.class);
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final LancamentoService lancamentoService = mock(LancamentoService.class);
    private final MovimentacaoService service = new MovimentacaoService(
            movimentacaoRepository, contaBancariaRepository, contaBancariaService, categoriaRepository, lancamentoService);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);
    private final UUID contaOrigemId = UUID.randomUUID();
    private final UUID contaDestinoId = UUID.randomUUID();
    private final LocalDate hoje = LocalDate.now();

    private ContaBancaria contaQualquer() {
        return ContaBancaria.novo(ambienteFinanceiroId, "Conta", TipoContaBancaria.CORRENTE, BigDecimal.ZERO);
    }

    @Test
    void extratoDeContaInexistenteLancaRecursoNaoEncontrado() {
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaOrigemId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.extrato(contexto, contaOrigemId, null, null, null, null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CONTA_BANCARIA_NAO_ENCONTRADA"));
    }

    @Test
    void extratoSemProximaPaginaNaoRetornaCursor() {
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaOrigemId, ambienteFinanceiroId))
                .thenReturn(Optional.of(contaQualquer()));
        Movimentacao movimentacao = Movimentacao.paraLancamento(ambienteFinanceiroId, contaOrigemId,
                TipoMovimentacao.DEBITO, new BigDecimal("10.00"), hoje, "Descricao", UUID.randomUUID());
        when(movimentacaoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(paginaCom(List.of(movimentacao)));

        PaginaMovimentacoes pagina = service.extrato(contexto, contaOrigemId, null, null, null, null);

        assertThat(pagina.itens()).hasSize(1);
        assertThat(pagina.proximoCursor()).isNull();
    }

    @Test
    void extratoComLimiteForaDoIntervaloLancaRegraDeNegocio() {
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaOrigemId, ambienteFinanceiroId))
                .thenReturn(Optional.of(contaQualquer()));

        assertThatThrownBy(() -> service.extrato(contexto, contaOrigemId, null, null, null, 0))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("LIMITE_INVALIDO"));
    }

    @Test
    void criarTransferenciaParaAMesmaContaLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.criarTransferencia(contexto, contaOrigemId, contaOrigemId,
                new BigDecimal("50.00"), hoje, "Reserva"))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TRANSFERENCIA_MESMA_CONTA"));
    }

    @Test
    void criarTransferenciaComContaDeOrigemDeOutroAmbienteLancaRecursoNaoEncontrado() {
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaOrigemId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarTransferencia(contexto, contaOrigemId, contaDestinoId,
                new BigDecimal("50.00"), hoje, "Reserva"))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CONTA_BANCARIA_NAO_ENCONTRADA"));
    }

    @Test
    void criarTransferenciaComContaDeDestinoDeOutroAmbienteLancaRecursoNaoEncontrado() {
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaOrigemId, ambienteFinanceiroId))
                .thenReturn(Optional.of(contaQualquer()));
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaDestinoId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarTransferencia(contexto, contaOrigemId, contaDestinoId,
                new BigDecimal("50.00"), hoje, "Reserva"))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CONTA_BANCARIA_NAO_ENCONTRADA"));
    }

    @Test
    void criarTransferenciaComReferenciasValidasCriaAsDuasMovimentacoesComOMesmoGrupoERecalculaOsDoisSaldos() {
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaOrigemId, ambienteFinanceiroId))
                .thenReturn(Optional.of(contaQualquer()));
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaDestinoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(contaQualquer()));
        UUID grupoTransferenciaId = UUID.randomUUID();
        when(movimentacaoRepository.gerarUuidV7()).thenReturn(grupoTransferenciaId);

        TransferenciaResumo resumo = service.criarTransferencia(contexto, contaOrigemId, contaDestinoId,
                new BigDecimal("80.00"), hoje, "Reserva mensal");

        assertThat(resumo.origem().tipo()).isEqualTo(TipoMovimentacao.DEBITO);
        assertThat(resumo.origem().contaBancariaId()).isEqualTo(contaOrigemId);
        assertThat(resumo.origem().grupoTransferenciaId()).isEqualTo(grupoTransferenciaId);
        assertThat(resumo.origem().lancamentoId()).isNull();
        assertThat(resumo.destino().tipo()).isEqualTo(TipoMovimentacao.CREDITO);
        assertThat(resumo.destino().contaBancariaId()).isEqualTo(contaDestinoId);
        assertThat(resumo.destino().grupoTransferenciaId()).isEqualTo(grupoTransferenciaId);

        ArgumentCaptor<Movimentacao> capturadas = ArgumentCaptor.forClass(Movimentacao.class);
        verify(movimentacaoRepository, times(2)).save(capturadas.capture());
        assertThat(capturadas.getAllValues()).hasSize(2);

        verify(contaBancariaService).recalcularSaldo(ambienteFinanceiroId, contaOrigemId);
        verify(contaBancariaService).recalcularSaldo(ambienteFinanceiroId, contaDestinoId);
    }

    private Page<Movimentacao> paginaCom(List<Movimentacao> itens) {
        return new PageImpl<>(itens);
    }

    /** ADR-014: criar avulsa. */
    @Test
    void criarAvulsaComReferenciasValidasPersisteERecalculaOSaldo() {
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaOrigemId, ambienteFinanceiroId))
                .thenReturn(Optional.of(contaQualquer()));

        MovimentacaoResumo resumo = service.criar(contexto, contaOrigemId, TipoMovimentacao.DEBITO,
                new BigDecimal("12.90"), hoje, "Tarifa de manutenção", null);

        assertThat(resumo.origem()).isEqualTo(OrigemMovimentacao.MANUAL);
        assertThat(resumo.categoriaId()).isNull();
        verify(movimentacaoRepository).save(any(Movimentacao.class));
        verify(contaBancariaService).recalcularSaldo(ambienteFinanceiroId, contaOrigemId);
    }

    @Test
    void criarAvulsaComContaDeOutroAmbienteLancaRecursoNaoEncontrado() {
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaOrigemId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(contexto, contaOrigemId, TipoMovimentacao.DEBITO,
                new BigDecimal("12.90"), hoje, "Tarifa", null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CONTA_BANCARIA_NAO_ENCONTRADA"));
    }

    @Test
    void criarAvulsaComCategoriaDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID categoriaId = UUID.randomUUID();
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaOrigemId, ambienteFinanceiroId))
                .thenReturn(Optional.of(contaQualquer()));
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(contexto, contaOrigemId, TipoMovimentacao.DEBITO,
                new BigDecimal("12.90"), hoje, "Tarifa", categoriaId))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CATEGORIA_NAO_ENCONTRADA"));
    }

    /** ADR-014: editar so aceita avulsa. */
    @Test
    void editarUmaAvulsaAlteraOsCamposERecalculaOSaldo() {
        UUID movimentacaoId = UUID.randomUUID();
        UUID categoriaId = UUID.randomUUID();
        Movimentacao movimentacao = Movimentacao.paraAvulsa(ambienteFinanceiroId, contaOrigemId,
                TipoMovimentacao.DEBITO, new BigDecimal("12.90"), hoje, "Tarifa", null);
        when(movimentacaoRepository.findByIdAndAmbienteFinanceiroId(movimentacaoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(movimentacao));
        Categoria categoria = Categoria.novo(ambienteFinanceiroId, "Bancárias", TipoCategoria.DESPESA, null);
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId))
                .thenReturn(Optional.of(categoria));

        MovimentacaoResumo resumo = service.editar(contexto, movimentacaoId, TipoMovimentacao.DEBITO,
                new BigDecimal("20.00"), hoje, "Tarifa corrigida", categoriaId);

        assertThat(resumo.valor()).isEqualByComparingTo("20.00");
        assertThat(resumo.categoriaId()).isEqualTo(categoriaId);
        verify(contaBancariaService).recalcularSaldo(ambienteFinanceiroId, contaOrigemId);
    }

    @Test
    void editarUmaMovimentacaoDeLiquidacaoLancaRegraDeNegocio() {
        UUID movimentacaoId = UUID.randomUUID();
        Movimentacao movimentacao = Movimentacao.paraLancamento(ambienteFinanceiroId, contaOrigemId,
                TipoMovimentacao.DEBITO, new BigDecimal("100.00"), hoje, "Aluguel", UUID.randomUUID());
        when(movimentacaoRepository.findByIdAndAmbienteFinanceiroId(movimentacaoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(movimentacao));

        assertThatThrownBy(() -> service.editar(contexto, movimentacaoId, TipoMovimentacao.DEBITO,
                new BigDecimal("110.00"), hoje, "Aluguel", null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("MOVIMENTACAO_NAO_EDITAVEL"));
    }

    @Test
    void editarUmaMovimentacaoInexistenteLancaRecursoNaoEncontrado() {
        UUID movimentacaoId = UUID.randomUUID();
        when(movimentacaoRepository.findByIdAndAmbienteFinanceiroId(movimentacaoId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editar(contexto, movimentacaoId, TipoMovimentacao.DEBITO,
                new BigDecimal("10.00"), hoje, "Descrição", null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("MOVIMENTACAO_NAO_ENCONTRADA"));
    }

    /** ADR-014: excluir varia por origem. */
    @Test
    void excluirUmaAvulsaApagaERecalculaOSaldo() {
        UUID movimentacaoId = UUID.randomUUID();
        Movimentacao movimentacao = Movimentacao.paraAvulsa(ambienteFinanceiroId, contaOrigemId,
                TipoMovimentacao.DEBITO, new BigDecimal("12.90"), hoje, "Tarifa", null);
        when(movimentacaoRepository.findByIdAndAmbienteFinanceiroId(movimentacaoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(movimentacao));

        service.excluir(contexto, movimentacaoId);

        verify(movimentacaoRepository).delete(movimentacao);
        verify(contaBancariaService).recalcularSaldo(ambienteFinanceiroId, contaOrigemId);
    }

    @Test
    void excluirUmaDeLiquidacaoDelegaParaReabrirDoLancamento() {
        UUID movimentacaoId = UUID.randomUUID();
        UUID lancamentoId = UUID.randomUUID();
        Movimentacao movimentacao = Movimentacao.paraLancamento(ambienteFinanceiroId, contaOrigemId,
                TipoMovimentacao.DEBITO, new BigDecimal("100.00"), hoje, "Aluguel", lancamentoId);
        when(movimentacaoRepository.findByIdAndAmbienteFinanceiroId(movimentacaoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(movimentacao));
        when(lancamentoService.reabrir(contexto, lancamentoId)).thenReturn(new LancamentoResumo(lancamentoId, null,
                null, null, null, null, null, null, null, null, null, null, "Aluguel", null, null));

        service.excluir(contexto, movimentacaoId);

        verify(lancamentoService).reabrir(contexto, lancamentoId);
        verify(movimentacaoRepository, org.mockito.Mockito.never()).delete(any(Movimentacao.class));
    }

    @Test
    void excluirUmaDeTransferenciaApagaAsDuasPernasERecalculaOsDoisSaldos() {
        UUID movimentacaoId = UUID.randomUUID();
        UUID grupoTransferenciaId = UUID.randomUUID();
        Movimentacao debito = Movimentacao.paraTransferencia(ambienteFinanceiroId, contaOrigemId,
                TipoMovimentacao.DEBITO, new BigDecimal("80.00"), hoje, "Reserva", grupoTransferenciaId);
        Movimentacao credito = Movimentacao.paraTransferencia(ambienteFinanceiroId, contaDestinoId,
                TipoMovimentacao.CREDITO, new BigDecimal("80.00"), hoje, "Reserva", grupoTransferenciaId);
        when(movimentacaoRepository.findByIdAndAmbienteFinanceiroId(movimentacaoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(debito));
        when(movimentacaoRepository.findAllByAmbienteFinanceiroIdAndGrupoTransferenciaId(ambienteFinanceiroId, grupoTransferenciaId))
                .thenReturn(List.of(debito, credito));

        service.excluir(contexto, movimentacaoId);

        verify(movimentacaoRepository).deleteAll(List.of(debito, credito));
        verify(contaBancariaService).recalcularSaldo(ambienteFinanceiroId, contaOrigemId);
        verify(contaBancariaService).recalcularSaldo(ambienteFinanceiroId, contaDestinoId);
    }

    @Test
    void excluirUmaMovimentacaoInexistenteLancaRecursoNaoEncontrado() {
        UUID movimentacaoId = UUID.randomUUID();
        when(movimentacaoRepository.findByIdAndAmbienteFinanceiroId(movimentacaoId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(contexto, movimentacaoId))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("MOVIMENTACAO_NAO_ENCONTRADA"));
    }
}
