package br.com.unifinancas.application.fatura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.cartao.Cartao;
import br.com.unifinancas.domain.cartao.CartaoRepository;
import br.com.unifinancas.domain.fatura.Fatura;
import br.com.unifinancas.domain.fatura.FaturaRepository;
import br.com.unifinancas.domain.fatura.StatusFatura;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.OrigemLancamento;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class FaturaServiceTest {

    private final FaturaRepository faturaRepository = mock(FaturaRepository.class);
    private final CartaoRepository cartaoRepository = mock(CartaoRepository.class);
    private final LancamentoRepository lancamentoRepository = mock(LancamentoRepository.class);
    private final FaturaService service = new FaturaService(faturaRepository, cartaoRepository, lancamentoRepository);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);
    private final UUID cartaoId = UUID.randomUUID();
    private final UUID faturaId = UUID.randomUUID();
    private final LocalDate hoje = LocalDate.now();

    private Cartao cartaoComTaxa(BigDecimal taxa) {
        Cartao cartao = Cartao.novo(ambienteFinanceiroId, null, "Nubank", new BigDecimal("5000.00"), 10, 17, taxa);
        ReflectionTestUtils.setField(cartao, "id", cartaoId);
        return cartao;
    }

    private Fatura faturaComSaldo(Cartao cartao, BigDecimal valorTotal) {
        Fatura fatura = Fatura.novoParaCompetencia(cartao, YearMonth.now());
        ReflectionTestUtils.setField(fatura, "id", faturaId);
        fatura.adicionarValor(valorTotal);
        fatura.fechar();
        return fatura;
    }

    @Test
    void pagarOValorTotalQuitaAFaturaSemGerarJuros() {
        Cartao cartao = cartaoComTaxa(new BigDecimal("12.00"));
        Fatura fatura = faturaComSaldo(cartao, new BigDecimal("300.00"));
        when(cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, ambienteFinanceiroId)).thenReturn(Optional.of(cartao));
        when(faturaRepository.findByIdAndAmbienteFinanceiroId(faturaId, ambienteFinanceiroId)).thenReturn(Optional.of(fatura));

        FaturaResumo resumo = service.pagar(contexto, cartaoId, faturaId, new BigDecimal("300.00"), hoje);

        assertThat(resumo.status()).isEqualTo(StatusFatura.PAGA);
        verify(lancamentoRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /** RN-11, ADR-017: pagamento parcial gera o Lancamento de juros sobre o saldo remanescente APOS o pagamento. */
    @Test
    void pagarParcialGeraLancamentoDeJurosSobreOSaldoRemanescente() {
        Cartao cartao = cartaoComTaxa(new BigDecimal("10.00"));
        Fatura fatura = faturaComSaldo(cartao, new BigDecimal("300.00"));
        when(cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, ambienteFinanceiroId)).thenReturn(Optional.of(cartao));
        when(faturaRepository.findByIdAndAmbienteFinanceiroId(faturaId, ambienteFinanceiroId)).thenReturn(Optional.of(fatura));

        FaturaResumo resumo = service.pagar(contexto, cartaoId, faturaId, new BigDecimal("100.00"), hoje);

        assertThat(resumo.status()).isEqualTo(StatusFatura.PAGA_PARCIAL);
        assertThat(resumo.valorPago()).isEqualByComparingTo("100.00");

        // saldo remanescente = 200.00, taxa 10% -> juros = 20.00
        ArgumentCaptor<LancamentoFinanceiro> capturado = ArgumentCaptor.forClass(LancamentoFinanceiro.class);
        verify(lancamentoRepository).save(capturado.capture());
        assertThat(capturado.getValue().getValor()).isEqualByComparingTo("20.00");
        assertThat(capturado.getValue().getOrigem()).isEqualTo(OrigemLancamento.FATURA);
        assertThat(capturado.getValue().getFaturaId()).isEqualTo(faturaId);
        assertThat(capturado.getValue().getCartaoId()).isEqualTo(cartaoId);
    }

    @Test
    void pagarParcialComTaxaZeroNaoGeraLancamentoDeJuros() {
        Cartao cartao = cartaoComTaxa(BigDecimal.ZERO);
        Fatura fatura = faturaComSaldo(cartao, new BigDecimal("300.00"));
        when(cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, ambienteFinanceiroId)).thenReturn(Optional.of(cartao));
        when(faturaRepository.findByIdAndAmbienteFinanceiroId(faturaId, ambienteFinanceiroId)).thenReturn(Optional.of(fatura));

        service.pagar(contexto, cartaoId, faturaId, new BigDecimal("100.00"), hoje);

        verify(lancamentoRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void pagarUmaFaturaDeOutroCartaoLancaRecursoNaoEncontrado() {
        Cartao cartao = cartaoComTaxa(null);
        Fatura faturaDeOutroCartao = Fatura.novoParaCompetencia(cartaoComTaxa(null), YearMonth.now());
        ReflectionTestUtils.setField(faturaDeOutroCartao, "cartaoId", UUID.randomUUID());
        ReflectionTestUtils.setField(faturaDeOutroCartao, "id", faturaId);
        when(cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, ambienteFinanceiroId)).thenReturn(Optional.of(cartao));
        when(faturaRepository.findByIdAndAmbienteFinanceiroId(faturaId, ambienteFinanceiroId))
                .thenReturn(Optional.of(faturaDeOutroCartao));

        assertThatThrownBy(() -> service.pagar(contexto, cartaoId, faturaId, BigDecimal.TEN, hoje))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("FATURA_NAO_ENCONTRADA"));
    }

    @Test
    void listarValidaQueOCartaoPertenceAoAmbiente() {
        when(cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(contexto, cartaoId))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CARTAO_NAO_ENCONTRADO"));
    }

    /** RN-11, ADR-017: parcela o saldo remanescente em N Lancamentos DESPESA iguais, vinculados a Fatura, e marca a Fatura como PAGA. */
    @Test
    void parcelarUmaFaturaAtrasadaCriaNParcelasEMarcaComoPaga() {
        Cartao cartao = cartaoComTaxa(null);
        Fatura fatura = faturaComSaldo(cartao, new BigDecimal("300.00"));
        fatura.marcarComoAtrasada();
        when(cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, ambienteFinanceiroId)).thenReturn(Optional.of(cartao));
        when(faturaRepository.findByIdAndAmbienteFinanceiroId(faturaId, ambienteFinanceiroId)).thenReturn(Optional.of(fatura));

        FaturaResumo resumo = service.parcelar(contexto, cartaoId, faturaId, 3);

        assertThat(resumo.status()).isEqualTo(StatusFatura.PAGA);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LancamentoFinanceiro>> capturado = ArgumentCaptor.forClass(List.class);
        verify(lancamentoRepository).saveAll(capturado.capture());
        List<LancamentoFinanceiro> parcelas = capturado.getValue();
        assertThat(parcelas).hasSize(3);
        assertThat(parcelas).allSatisfy(parcela -> {
            assertThat(parcela.getOrigem()).isEqualTo(OrigemLancamento.FATURA);
            assertThat(parcela.getFaturaId()).isEqualTo(faturaId);
        });
        BigDecimal somaDasParcelas = parcelas.stream().map(LancamentoFinanceiro::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somaDasParcelas).isEqualByComparingTo("300.00");
    }

    @Test
    void parcelarComMenosDeDuasParcelasLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.parcelar(contexto, cartaoId, faturaId, 1))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("NUMERO_PARCELAS_INVALIDO"));
    }

    @Test
    void parcelarUmaFaturaQueNaoEstaAtrasadaLancaRegraDeNegocio() {
        Cartao cartao = cartaoComTaxa(null);
        Fatura fatura = faturaComSaldo(cartao, new BigDecimal("300.00"));
        when(cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, ambienteFinanceiroId)).thenReturn(Optional.of(cartao));
        when(faturaRepository.findByIdAndAmbienteFinanceiroId(faturaId, ambienteFinanceiroId)).thenReturn(Optional.of(fatura));

        assertThatThrownBy(() -> service.parcelar(contexto, cartaoId, faturaId, 3))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("FATURA_NAO_PODE_SER_PARCELADA"));
    }
}
