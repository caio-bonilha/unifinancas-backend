package br.com.unifinancas.application.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.application.lancamento.LancamentoResumo;
import br.com.unifinancas.application.lancamento.LancamentoService;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.contabancaria.ContaBancaria;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.contabancaria.TipoContaBancaria;
import br.com.unifinancas.domain.lancamento.FormaPagamento;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.OrigemLancamento;
import br.com.unifinancas.domain.lancamento.StatusLancamento;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.infrastructure.importacao.TabelaImportada;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ImportacaoServiceTest {

    private final LancamentoRepository lancamentoRepository = mock(LancamentoRepository.class);
    private final ContaBancariaRepository contaBancariaRepository = mock(ContaBancariaRepository.class);
    private final LancamentoService lancamentoService = mock(LancamentoService.class);
    private final ImportacaoService service =
            new ImportacaoService(lancamentoRepository, contaBancariaRepository, lancamentoService);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);
    private final UUID contaBancariaId = UUID.randomUUID();

    @BeforeEach
    void contaBancariaValidaPorPadrao() {
        ContaBancaria conta = ContaBancaria.novo(ambienteFinanceiroId, "Nubank", TipoContaBancaria.CORRENTE, BigDecimal.ZERO);
        ReflectionTestUtils.setField(conta, "id", contaBancariaId);
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteFinanceiroId))
                .thenReturn(Optional.of(conta));
        when(lancamentoRepository.categoriasPorDescricaoNormalizadaOrdenadasPorFrequencia(any(), any())).thenReturn(List.of());
    }

    private LancamentoFinanceiro lancamentoExistente(UUID categoriaId) {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("45.90"), LocalDate.now(), LocalDate.now(), categoriaId, null, contaBancariaId,
                FormaPagamento.DEBITO, "Padaria", null);
        ReflectionTestUtils.setField(lancamento, "id", UUID.randomUUID());
        return lancamento;
    }

    @Test
    void transacaoComFitidJaImportadoEMarcadaComoDuplicada() {
        TransacaoImportada transacao =
                new TransacaoImportada(LocalDate.now(), new BigDecimal("-45.90"), "Padaria", "FITID-123");
        LancamentoFinanceiro existente = lancamentoExistente(null);
        when(lancamentoRepository.findByAmbienteFinanceiroIdAndContaBancariaIdAndIdentificadorExterno(
                        ambienteFinanceiroId, contaBancariaId, "FITID-123"))
                .thenReturn(Optional.of(existente));

        List<TransacaoAnalisada> resultado = service.analisar(contexto, contaBancariaId, List.of(transacao));

        assertThat(resultado).hasSize(1);
        TransacaoAnalisada analisada = resultado.get(0);
        assertThat(analisada.duplicada()).isTrue();
        assertThat(analisada.identificadorExternoResolvido()).isEqualTo("FITID-123");
        assertThat(analisada.lancamentoDuplicadoId()).isEqualTo(existente.getId());
        assertThat(analisada.categoriaSugeridaId()).isNull();
    }

    @Test
    void transacaoComFitidNovoNaoEDuplicadaESugereCategoria() {
        UUID categoriaId = UUID.randomUUID();
        TransacaoImportada transacao =
                new TransacaoImportada(LocalDate.now(), new BigDecimal("-45.90"), "Padaria", "FITID-999");
        when(lancamentoRepository.findByAmbienteFinanceiroIdAndContaBancariaIdAndIdentificadorExterno(
                        ambienteFinanceiroId, contaBancariaId, "FITID-999"))
                .thenReturn(Optional.empty());
        when(lancamentoRepository.categoriasPorDescricaoNormalizadaOrdenadasPorFrequencia(ambienteFinanceiroId, "padaria"))
                .thenReturn(List.of(categoriaId));

        TransacaoAnalisada analisada = service.analisar(contexto, contaBancariaId, List.of(transacao)).get(0);

        assertThat(analisada.duplicada()).isFalse();
        assertThat(analisada.categoriaSugeridaId()).isEqualTo(categoriaId);
    }

    @Test
    void semFitidResolveUmIdentificadorSinteticoDeterministico() {
        TransacaoImportada transacao = new TransacaoImportada(LocalDate.of(2026, 8, 5), new BigDecimal("-45.90"), "Padaria", null);
        when(lancamentoRepository.findByAmbienteFinanceiroIdAndContaBancariaIdAndIdentificadorExterno(
                        eq(ambienteFinanceiroId), eq(contaBancariaId), anyString()))
                .thenReturn(Optional.empty());

        TransacaoAnalisada primeira = service.analisar(contexto, contaBancariaId, List.of(transacao)).get(0);
        TransacaoAnalisada segunda = service.analisar(contexto, contaBancariaId, List.of(transacao)).get(0);

        assertThat(primeira.identificadorExternoResolvido()).isNotBlank();
        assertThat(primeira.identificadorExternoResolvido()).isEqualTo(segunda.identificadorExternoResolvido());
    }

    @Test
    void semFitidTransacoesDiferentesGeramIdentificadoresDiferentes() {
        TransacaoImportada transacaoA = new TransacaoImportada(LocalDate.of(2026, 8, 5), new BigDecimal("-45.90"), "Padaria", null);
        TransacaoImportada transacaoB = new TransacaoImportada(LocalDate.of(2026, 8, 6), new BigDecimal("-45.90"), "Padaria", null);
        when(lancamentoRepository.findByAmbienteFinanceiroIdAndContaBancariaIdAndIdentificadorExterno(any(), any(), anyString()))
                .thenReturn(Optional.empty());

        List<TransacaoAnalisada> resultado = service.analisar(contexto, contaBancariaId, List.of(transacaoA, transacaoB));

        assertThat(resultado.get(0).identificadorExternoResolvido()).isNotEqualTo(resultado.get(1).identificadorExternoResolvido());
    }

    @Test
    void sugestaoDeCategoriaIgnoraDiferencaDeCaixaEEspacosNasPontas() {
        UUID categoriaId = UUID.randomUUID();
        TransacaoImportada transacao =
                new TransacaoImportada(LocalDate.now(), new BigDecimal("-10.00"), "  UBER Viagem  ", "FITID-1");
        when(lancamentoRepository.findByAmbienteFinanceiroIdAndContaBancariaIdAndIdentificadorExterno(
                        ambienteFinanceiroId, contaBancariaId, "FITID-1"))
                .thenReturn(Optional.empty());
        when(lancamentoRepository.categoriasPorDescricaoNormalizadaOrdenadasPorFrequencia(ambienteFinanceiroId, "uber viagem"))
                .thenReturn(List.of(categoriaId));

        TransacaoAnalisada analisada = service.analisar(contexto, contaBancariaId, List.of(transacao)).get(0);

        assertThat(analisada.categoriaSugeridaId()).isEqualTo(categoriaId);
    }

    @Test
    void semCategoriaEncontradaCategoriaSugeridaFicaNula() {
        TransacaoImportada transacao = new TransacaoImportada(LocalDate.now(), new BigDecimal("-10.00"), "Compra nova", "FITID-2");
        when(lancamentoRepository.findByAmbienteFinanceiroIdAndContaBancariaIdAndIdentificadorExterno(
                        ambienteFinanceiroId, contaBancariaId, "FITID-2"))
                .thenReturn(Optional.empty());

        TransacaoAnalisada analisada = service.analisar(contexto, contaBancariaId, List.of(transacao)).get(0);

        assertThat(analisada.categoriaSugeridaId()).isNull();
    }

    @Test
    void contaBancariaDeOutroAmbienteLancaRecursoNaoEncontradoAoAnalisar() {
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analisar(contexto, contaBancariaId, List.of()))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CONTA_BANCARIA_NAO_ENCONTRADA"));
    }

    @Test
    void lerTabelaCsvDevolveCabecalhoELinhas() {
        String csv = "Data,Descricao,Valor\n05/08/2026,Padaria,-45.90\n";

        TabelaImportada tabela = service.lerTabela(contexto, contaBancariaId, TipoArquivoPlanilha.CSV, csv.getBytes());

        assertThat(tabela.cabecalho()).containsExactly("Data", "Descricao", "Valor");
        assertThat(tabela.linhas()).hasSize(1);
    }

    @Test
    void lerTabelaComArquivoInvalidoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.lerTabela(contexto, contaBancariaId, TipoArquivoPlanilha.CSV, "".getBytes()))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("ARQUIVO_INVALIDO"));
    }

    @Test
    void analisarOfxInvalidoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.analisarOfx(contexto, contaBancariaId, "isto nao e ofx".getBytes()))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("ARQUIVO_INVALIDO"));
    }

    private LancamentoResumo lancamentoResumoFake() {
        return new LancamentoResumo(UUID.randomUUID(), TipoLancamento.DESPESA, StatusLancamento.PAGO,
                new BigDecimal("45.90"), LocalDate.now(), LocalDate.now(), LocalDate.now(), null, null, contaBancariaId,
                FormaPagamento.TRANSFERENCIA, OrigemLancamento.IMPORTACAO, "Padaria", null, null);
    }

    @Test
    void confirmarCriaLancamentoParaTransacaoNovaEDerivaTipoDoSinalDoValor() {
        TransacaoConfirmada transacao = new TransacaoConfirmada(LocalDate.now(), new BigDecimal("-45.90"), "Padaria", null, "FITID-1");
        when(lancamentoRepository.findByAmbienteFinanceiroIdAndContaBancariaIdAndIdentificadorExterno(
                        ambienteFinanceiroId, contaBancariaId, "FITID-1"))
                .thenReturn(Optional.empty());
        when(lancamentoService.criarDeImportacao(contexto, TipoLancamento.DESPESA, new BigDecimal("45.90"), transacao.data(),
                        null, contaBancariaId, "Padaria", "FITID-1"))
                .thenReturn(lancamentoResumoFake());

        List<LancamentoResumo> criados = service.confirmar(contexto, contaBancariaId, List.of(transacao));

        assertThat(criados).hasSize(1);
        verify(lancamentoService).criarDeImportacao(contexto, TipoLancamento.DESPESA, new BigDecimal("45.90"), transacao.data(),
                null, contaBancariaId, "Padaria", "FITID-1");
    }

    @Test
    void confirmarValorPositivoDerivaTipoReceita() {
        TransacaoConfirmada transacao = new TransacaoConfirmada(LocalDate.now(), new BigDecimal("1500.00"), "Salario", null, "FITID-2");
        when(lancamentoRepository.findByAmbienteFinanceiroIdAndContaBancariaIdAndIdentificadorExterno(
                        ambienteFinanceiroId, contaBancariaId, "FITID-2"))
                .thenReturn(Optional.empty());
        when(lancamentoService.criarDeImportacao(contexto, TipoLancamento.RECEITA, new BigDecimal("1500.00"), transacao.data(),
                        null, contaBancariaId, "Salario", "FITID-2"))
                .thenReturn(lancamentoResumoFake());

        service.confirmar(contexto, contaBancariaId, List.of(transacao));

        verify(lancamentoService).criarDeImportacao(contexto, TipoLancamento.RECEITA, new BigDecimal("1500.00"), transacao.data(),
                null, contaBancariaId, "Salario", "FITID-2");
    }

    @Test
    void confirmarPulaTransacaoJaImportadaSemErro() {
        TransacaoConfirmada transacao = new TransacaoConfirmada(LocalDate.now(), new BigDecimal("-45.90"), "Padaria", null, "FITID-3");
        when(lancamentoRepository.findByAmbienteFinanceiroIdAndContaBancariaIdAndIdentificadorExterno(
                        ambienteFinanceiroId, contaBancariaId, "FITID-3"))
                .thenReturn(Optional.of(lancamentoExistente(null)));

        List<LancamentoResumo> criados = service.confirmar(contexto, contaBancariaId, List.of(transacao));

        assertThat(criados).isEmpty();
        verify(lancamentoService, never()).criarDeImportacao(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
