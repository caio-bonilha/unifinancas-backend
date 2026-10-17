package br.com.unifinancas.application.orcamentometa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.unifinancas.application.notificacao.NotificacaoService;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.StatusVinculo;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import br.com.unifinancas.domain.categoria.Categoria;
import br.com.unifinancas.domain.categoria.CategoriaRepository;
import br.com.unifinancas.domain.categoria.TipoCategoria;
import br.com.unifinancas.domain.contabancaria.ContaBancaria;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.contabancaria.TipoContaBancaria;
import br.com.unifinancas.domain.lancamento.LancamentoCriadoEvent;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.notificacao.CanalNotificacao;
import br.com.unifinancas.domain.notificacao.TipoNotificacao;
import br.com.unifinancas.domain.orcamentometa.OrcamentoMeta;
import br.com.unifinancas.domain.orcamentometa.OrcamentoMetaRepository;
import br.com.unifinancas.domain.orcamentometa.StatusOrcamentoMeta;
import br.com.unifinancas.domain.orcamentometa.TipoOrcamentoMeta;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class OrcamentoMetaServiceTest {

    private final OrcamentoMetaRepository orcamentoMetaRepository = mock(OrcamentoMetaRepository.class);
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final ContaBancariaRepository contaBancariaRepository = mock(ContaBancariaRepository.class);
    private final LancamentoRepository lancamentoRepository = mock(LancamentoRepository.class);
    private final UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository =
            mock(UsuarioAmbienteFinanceiroRepository.class);
    private final NotificacaoService notificacaoService = mock(NotificacaoService.class);
    private final OrcamentoMetaService service = new OrcamentoMetaService(orcamentoMetaRepository, categoriaRepository,
            contaBancariaRepository, lancamentoRepository, usuarioAmbienteFinanceiroRepository, notificacaoService);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);
    private final UUID categoriaId = UUID.randomUUID();
    private final UUID contaBancariaId = UUID.randomUUID();
    private final LocalDate hoje = LocalDate.now();

    @Test
    void criarOrcamentoComCategoriaValidaFunciona() {
        Categoria categoria = Categoria.novo(ambienteFinanceiroId, "Aluguel", TipoCategoria.DESPESA, null);
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.of(categoria));
        when(lancamentoRepository.somarDespesasPorCategoriaEPeriodo(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        OrcamentoMetaResumo resumo = service.criarOrcamento(
                contexto, categoriaId, new BigDecimal("500.00"), null, hoje, hoje.plusMonths(1));

        ArgumentCaptor<OrcamentoMeta> capturado = ArgumentCaptor.forClass(OrcamentoMeta.class);
        verify(orcamentoMetaRepository).save(capturado.capture());
        assertThat(capturado.getValue().getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(resumo.tipo()).isEqualTo(TipoOrcamentoMeta.ORCAMENTO);
        assertThat(resumo.valorLimite()).isEqualByComparingTo("500.00");
        assertThat(resumo.valorAtual()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resumo.percentualAtingido()).isEqualByComparingTo("0.00");
        assertThat(resumo.limiarAlertaPercentual()).isEqualByComparingTo("80");
    }

    @Test
    void criarOrcamentoComLimiarAlertaCustomizado() {
        Categoria categoria = Categoria.novo(ambienteFinanceiroId, "Aluguel", TipoCategoria.DESPESA, null);
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.of(categoria));
        when(lancamentoRepository.somarDespesasPorCategoriaEPeriodo(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        OrcamentoMetaResumo resumo = service.criarOrcamento(
                contexto, categoriaId, new BigDecimal("500.00"), new BigDecimal("70"), hoje, null);

        assertThat(resumo.limiarAlertaPercentual()).isEqualByComparingTo("70");
    }

    @Test
    void criarOrcamentoSemCategoriaLancaRegraDeNegocioNaoRecursoNaoEncontrado() {
        assertThatThrownBy(() -> service.criarOrcamento(contexto, null, new BigDecimal("500.00"), null, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo())
                        .isEqualTo("CATEGORIA_OBRIGATORIA_PARA_ORCAMENTO"));
    }

    @Test
    void criarOrcamentoComCategoriaDeOutroAmbienteLancaRecursoNaoEncontrado() {
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarOrcamento(contexto, categoriaId, new BigDecimal("500.00"), null, hoje, null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CATEGORIA_NAO_ENCONTRADA"));
    }

    @Test
    void criarMetaSemContaBancariaFuncionaSemProgressoCalculavel() {
        OrcamentoMetaResumo resumo = service.criarMeta(contexto, "Viagem", new BigDecimal("3000.00"), null, hoje, null);

        assertThat(resumo.tipo()).isEqualTo(TipoOrcamentoMeta.META);
        assertThat(resumo.valorAtual()).isNull();
        assertThat(resumo.percentualAtingido()).isNull();
    }

    @Test
    void criarMetaComContaBancariaValidaCalculaProgressoAPartirDoSaldo() {
        ContaBancaria conta = ContaBancaria.novo(ambienteFinanceiroId, "Poupanca", TipoContaBancaria.POUPANCA, new BigDecimal("750.00"));
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteFinanceiroId)).thenReturn(Optional.of(conta));

        OrcamentoMetaResumo resumo = service.criarMeta(contexto, "Viagem", new BigDecimal("3000.00"), contaBancariaId, hoje, null);

        assertThat(resumo.valorAtual()).isEqualByComparingTo("750.00");
        assertThat(resumo.percentualAtingido()).isEqualByComparingTo("25.00");
    }

    @Test
    void criarMetaComContaBancariaDeOutroAmbienteLancaRecursoNaoEncontrado() {
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criarMeta(contexto, "Viagem", new BigDecimal("3000.00"), contaBancariaId, hoje, null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CONTA_BANCARIA_NAO_ENCONTRADA"));
    }

    @Test
    void listarComProgressoRetornaOrcamentoComGastoDoMesSomado() {
        OrcamentoMeta orcamento = OrcamentoMeta.novoOrcamento(ambienteFinanceiroId, categoriaId, new BigDecimal("500.00"), null, hoje, null);
        ReflectionTestUtils.setField(orcamento, "id", UUID.randomUUID());
        when(orcamentoMetaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of(orcamento));
        when(lancamentoRepository.somarDespesasPorCategoriaEPeriodo(eq(ambienteFinanceiroId), eq(categoriaId), any(), any()))
                .thenReturn(new BigDecimal("350.00"));

        List<OrcamentoMetaResumo> resultado = service.listarComProgresso(contexto);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).valorAtual()).isEqualByComparingTo("350.00");
        assertThat(resultado.get(0).percentualAtingido()).isEqualByComparingTo("70.00");
    }

    @Test
    void semNenhumOrcamentoOuMetaRetornaListaVazia() {
        when(orcamentoMetaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of());

        assertThat(service.listarComProgresso(contexto)).isEmpty();
    }

    // --- T1.5.3: reacao a LancamentoCriadoEvent (Fluxo 6.4) ---

    private LancamentoCriadoEvent eventoDespesa(BigDecimal valor) {
        return new LancamentoCriadoEvent(UUID.randomUUID(), ambienteFinanceiroId, categoriaId, usuarioId,
                TipoLancamento.DESPESA, valor, Instant.now());
    }

    private UsuarioAmbienteFinanceiro membroAtivo() {
        return UsuarioAmbienteFinanceiro.vincularComPerfil(UUID.randomUUID(), ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);
    }

    private void stubOrcamentoAtivo(OrcamentoMeta orcamento) {
        when(orcamentoMetaRepository.findAllByAmbienteFinanceiroIdAndCategoriaIdAndTipoAndStatus(
                ambienteFinanceiroId, categoriaId, TipoOrcamentoMeta.ORCAMENTO, StatusOrcamentoMeta.ATIVO))
                .thenReturn(List.of(orcamento));
    }

    @Test
    void aoLancamentoCriadoIgnoraEventoQueNaoEDespesa() {
        LancamentoCriadoEvent evento = new LancamentoCriadoEvent(UUID.randomUUID(), ambienteFinanceiroId, categoriaId,
                usuarioId, TipoLancamento.RECEITA, new BigDecimal("100"), Instant.now());

        service.aoLancamentoCriado(evento);

        verifyNoInteractions(orcamentoMetaRepository, notificacaoService);
    }

    @Test
    void aoLancamentoCriadoIgnoraEventoSemCategoria() {
        LancamentoCriadoEvent evento = new LancamentoCriadoEvent(UUID.randomUUID(), ambienteFinanceiroId, null,
                usuarioId, TipoLancamento.DESPESA, new BigDecimal("100"), Instant.now());

        service.aoLancamentoCriado(evento);

        verifyNoInteractions(orcamentoMetaRepository, notificacaoService);
    }

    @Test
    void aoLancamentoCriadoDisparaAvisoQuandoCruzaOLimiarENotificaTodosOsMembrosAtivos() {
        OrcamentoMeta orcamento = OrcamentoMeta.novoOrcamento(ambienteFinanceiroId, categoriaId, new BigDecimal("500.00"), null, hoje, null);
        ReflectionTestUtils.setField(orcamento, "id", UUID.randomUUID());
        stubOrcamentoAtivo(orcamento);
        when(lancamentoRepository.somarDespesasPorCategoriaEPeriodo(eq(ambienteFinanceiroId), eq(categoriaId), any(), any()))
                .thenReturn(new BigDecimal("400.00"));
        UsuarioAmbienteFinanceiro membro1 = membroAtivo();
        UsuarioAmbienteFinanceiro membro2 = membroAtivo();
        when(usuarioAmbienteFinanceiroRepository.findAllByAmbienteFinanceiroIdAndStatus(ambienteFinanceiroId, StatusVinculo.ATIVO))
                .thenReturn(List.of(membro1, membro2));

        service.aoLancamentoCriado(eventoDespesa(new BigDecimal("400.00")));

        verify(notificacaoService).criar(eq(ambienteFinanceiroId), eq(membro1.getUsuarioId()), eq(TipoNotificacao.ORCAMENTO_LIMITE),
                any(), any(), eq("orcamento_meta"), eq(orcamento.getId()), eq(Set.of(CanalNotificacao.PUSH, CanalNotificacao.EMAIL)));
        verify(notificacaoService).criar(eq(ambienteFinanceiroId), eq(membro2.getUsuarioId()), eq(TipoNotificacao.ORCAMENTO_LIMITE),
                any(), any(), eq("orcamento_meta"), eq(orcamento.getId()), any());
        verify(orcamentoMetaRepository).save(orcamento);
        assertThat(orcamento.getUltimoAlertaTipo()).isEqualTo(br.com.unifinancas.domain.orcamentometa.SeveridadeAlertaOrcamento.AVISO);
    }

    @Test
    void aoLancamentoCriadoDisparaEstouradoQuandoAtingeOuUltrapassaOLimite() {
        OrcamentoMeta orcamento = OrcamentoMeta.novoOrcamento(ambienteFinanceiroId, categoriaId, new BigDecimal("500.00"), null, hoje, null);
        ReflectionTestUtils.setField(orcamento, "id", UUID.randomUUID());
        stubOrcamentoAtivo(orcamento);
        when(lancamentoRepository.somarDespesasPorCategoriaEPeriodo(eq(ambienteFinanceiroId), eq(categoriaId), any(), any()))
                .thenReturn(new BigDecimal("600.00"));
        when(usuarioAmbienteFinanceiroRepository.findAllByAmbienteFinanceiroIdAndStatus(ambienteFinanceiroId, StatusVinculo.ATIVO))
                .thenReturn(List.of(membroAtivo()));

        service.aoLancamentoCriado(eventoDespesa(new BigDecimal("600.00")));

        assertThat(orcamento.getUltimoAlertaTipo()).isEqualTo(br.com.unifinancas.domain.orcamentometa.SeveridadeAlertaOrcamento.ESTOURADO);
    }

    @Test
    void aoLancamentoCriadoNaoNotificaDeNovoNoMesmoPeriodoParaAMesmaSeveridade() {
        OrcamentoMeta orcamento = OrcamentoMeta.novoOrcamento(ambienteFinanceiroId, categoriaId, new BigDecimal("500.00"), null, hoje, null);
        ReflectionTestUtils.setField(orcamento, "id", UUID.randomUUID());
        orcamento.registrarAlertaEnviado(br.com.unifinancas.domain.orcamentometa.SeveridadeAlertaOrcamento.AVISO, hoje.withDayOfMonth(1));
        stubOrcamentoAtivo(orcamento);
        when(lancamentoRepository.somarDespesasPorCategoriaEPeriodo(eq(ambienteFinanceiroId), eq(categoriaId), any(), any()))
                .thenReturn(new BigDecimal("420.00"));

        service.aoLancamentoCriado(eventoDespesa(new BigDecimal("420.00")));

        verifyNoInteractions(notificacaoService);
        verify(orcamentoMetaRepository, never()).save(any());
    }

    @Test
    void aoLancamentoCriadoNotificaDeNovoAoSubirDeAvisoParaEstourado() {
        OrcamentoMeta orcamento = OrcamentoMeta.novoOrcamento(ambienteFinanceiroId, categoriaId, new BigDecimal("500.00"), null, hoje, null);
        ReflectionTestUtils.setField(orcamento, "id", UUID.randomUUID());
        orcamento.registrarAlertaEnviado(br.com.unifinancas.domain.orcamentometa.SeveridadeAlertaOrcamento.AVISO, hoje.withDayOfMonth(1));
        stubOrcamentoAtivo(orcamento);
        when(lancamentoRepository.somarDespesasPorCategoriaEPeriodo(eq(ambienteFinanceiroId), eq(categoriaId), any(), any()))
                .thenReturn(new BigDecimal("550.00"));
        when(usuarioAmbienteFinanceiroRepository.findAllByAmbienteFinanceiroIdAndStatus(ambienteFinanceiroId, StatusVinculo.ATIVO))
                .thenReturn(List.of(membroAtivo()));

        service.aoLancamentoCriado(eventoDespesa(new BigDecimal("550.00")));

        assertThat(orcamento.getUltimoAlertaTipo()).isEqualTo(br.com.unifinancas.domain.orcamentometa.SeveridadeAlertaOrcamento.ESTOURADO);
        verify(notificacaoService, times(1)).criar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aoLancamentoCriadoAbaixoDoLimiarNaoNotifica() {
        OrcamentoMeta orcamento = OrcamentoMeta.novoOrcamento(ambienteFinanceiroId, categoriaId, new BigDecimal("500.00"), null, hoje, null);
        ReflectionTestUtils.setField(orcamento, "id", UUID.randomUUID());
        stubOrcamentoAtivo(orcamento);
        when(lancamentoRepository.somarDespesasPorCategoriaEPeriodo(eq(ambienteFinanceiroId), eq(categoriaId), any(), any()))
                .thenReturn(new BigDecimal("100.00"));

        service.aoLancamentoCriado(eventoDespesa(new BigDecimal("100.00")));

        verifyNoInteractions(notificacaoService);
        verify(orcamentoMetaRepository, never()).save(any());
    }

    @Test
    void aoLancamentoCriadoRespeitaLimiarAlertaCustomizado() {
        OrcamentoMeta orcamento = OrcamentoMeta.novoOrcamento(
                ambienteFinanceiroId, categoriaId, new BigDecimal("500.00"), new BigDecimal("50"), hoje, null);
        ReflectionTestUtils.setField(orcamento, "id", UUID.randomUUID());
        stubOrcamentoAtivo(orcamento);
        when(lancamentoRepository.somarDespesasPorCategoriaEPeriodo(eq(ambienteFinanceiroId), eq(categoriaId), any(), any()))
                .thenReturn(new BigDecimal("260.00"));
        when(usuarioAmbienteFinanceiroRepository.findAllByAmbienteFinanceiroIdAndStatus(ambienteFinanceiroId, StatusVinculo.ATIVO))
                .thenReturn(List.of(membroAtivo()));

        service.aoLancamentoCriado(eventoDespesa(new BigDecimal("260.00")));

        assertThat(orcamento.getUltimoAlertaTipo()).isEqualTo(br.com.unifinancas.domain.orcamentometa.SeveridadeAlertaOrcamento.AVISO);
    }
}
