package br.com.unifinancas.domain.fatura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.unifinancas.domain.cartao.Cartao;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FaturaTest {

    private final UUID cartaoId = UUID.randomUUID();
    private final LocalDate competencia = LocalDate.of(2026, 8, 1);
    private final LocalDate dataFechamento = LocalDate.of(2026, 8, 10);
    private final LocalDate dataVencimento = LocalDate.of(2026, 8, 17);

    private Fatura novaFatura() {
        return Fatura.novo(cartaoId, competencia, dataFechamento, dataVencimento);
    }

    @Test
    void novaFaturaNasceAbertaComValoresZerados() {
        Fatura fatura = novaFatura();

        assertThat(fatura.getCartaoId()).isEqualTo(cartaoId);
        assertThat(fatura.getCompetencia()).isEqualTo(competencia);
        assertThat(fatura.getStatus()).isEqualTo(StatusFatura.ABERTA);
        assertThat(fatura.getValorTotal()).isEqualByComparingTo("0");
        assertThat(fatura.getValorPago()).isEqualByComparingTo("0");
        assertThat(fatura.saldoRemanescente()).isEqualByComparingTo("0");
    }

    @Test
    void adicionarValorSomaAoTotalEnquantoAberta() {
        Fatura fatura = novaFatura();

        fatura.adicionarValor(new BigDecimal("150.00"));
        fatura.adicionarValor(new BigDecimal("50.00"));

        assertThat(fatura.getValorTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    void adicionarValorAUmaFaturaNaoAbertaLancaRegraDeNegocio() {
        Fatura fatura = novaFatura();
        fatura.fechar();

        assertThatThrownBy(() -> fatura.adicionarValor(BigDecimal.TEN))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("FATURA_NAO_ABERTA"));
    }

    @Test
    void fecharMudaDeAbertaParaFechada() {
        Fatura fatura = novaFatura();

        fatura.fechar();

        assertThat(fatura.getStatus()).isEqualTo(StatusFatura.FECHADA);
    }

    @Test
    void fecharUmaFaturaJaFechadaLancaRegraDeNegocio() {
        Fatura fatura = novaFatura();
        fatura.fechar();

        assertThatThrownBy(fatura::fechar)
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TRANSICAO_INVALIDA"));
    }

    @Test
    void marcarComoAtrasadaMudaDeFechadaParaAtrasada() {
        Fatura fatura = novaFatura();
        fatura.fechar();

        fatura.marcarComoAtrasada();

        assertThat(fatura.getStatus()).isEqualTo(StatusFatura.ATRASADA);
        assertThat(fatura.podeSerParcelada()).isTrue();
    }

    @Test
    void marcarComoAtrasadaUmaFaturaAbertaLancaRegraDeNegocio() {
        assertThatThrownBy(novaFatura()::marcarComoAtrasada)
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TRANSICAO_INVALIDA"));
    }

    @Test
    void pagarOValorTotalMudaParaPaga() {
        Fatura fatura = novaFatura();
        fatura.adicionarValor(new BigDecimal("300.00"));
        fatura.fechar();

        fatura.pagar(new BigDecimal("300.00"));

        assertThat(fatura.getStatus()).isEqualTo(StatusFatura.PAGA);
        assertThat(fatura.getValorPago()).isEqualByComparingTo("300.00");
        assertThat(fatura.saldoRemanescente()).isEqualByComparingTo("0");
    }

    @Test
    void pagarParcialMudaParaPagaParcialEPermiteNovoPagamentoAteQuitar() {
        Fatura fatura = novaFatura();
        fatura.adicionarValor(new BigDecimal("300.00"));
        fatura.fechar();

        fatura.pagar(new BigDecimal("100.00"));
        assertThat(fatura.getStatus()).isEqualTo(StatusFatura.PAGA_PARCIAL);
        assertThat(fatura.saldoRemanescente()).isEqualByComparingTo("200.00");

        fatura.pagar(new BigDecimal("200.00"));
        assertThat(fatura.getStatus()).isEqualTo(StatusFatura.PAGA);
    }

    @Test
    void pagarUmaFaturaAindaAbertaLancaRegraDeNegocio() {
        assertThatThrownBy(() -> novaFatura().pagar(BigDecimal.TEN))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TRANSICAO_INVALIDA"));
    }

    @Test
    void pagarMaisDoQueOSaldoRemanescenteLancaRegraDeNegocio() {
        Fatura fatura = novaFatura();
        fatura.adicionarValor(new BigDecimal("100.00"));
        fatura.fechar();

        assertThatThrownBy(() -> fatura.pagar(new BigDecimal("200.00")))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("VALOR_MAIOR_QUE_SALDO"));
    }

    @Test
    void pagarValorZeroOuNegativoLancaRegraDeNegocio() {
        Fatura fatura = novaFatura();
        fatura.adicionarValor(new BigDecimal("100.00"));
        fatura.fechar();

        assertThatThrownBy(() -> fatura.pagar(BigDecimal.ZERO))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("VALOR_INVALIDO"));
    }

    @Test
    void faturaAtrasadaPodeReceberPagamentoParcialOuTotal() {
        Fatura fatura = novaFatura();
        fatura.adicionarValor(new BigDecimal("300.00"));
        fatura.fechar();
        fatura.marcarComoAtrasada();

        fatura.pagar(new BigDecimal("300.00"));

        assertThat(fatura.getStatus()).isEqualTo(StatusFatura.PAGA);
    }

    /** T2.1.2: vencimento no mesmo mes da competencia quando diaVencimento > diaFechamento. */
    @Test
    void novoParaCompetenciaComVencimentoDepoisDoFechamentoFicaNoMesmoMes() {
        Cartao cartao = Cartao.novo(UUID.randomUUID(), null, "Nubank", BigDecimal.TEN, 10, 17, null);
        ReflectionTestUtils.setField(cartao, "id", cartaoId);

        Fatura fatura = Fatura.novoParaCompetencia(cartao, YearMonth.of(2026, 8));

        assertThat(fatura.getCartaoId()).isEqualTo(cartaoId);
        assertThat(fatura.getCompetencia()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(fatura.getDataFechamento()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(fatura.getDataVencimento()).isEqualTo(LocalDate.of(2026, 8, 17));
    }

    /** T2.1.2: vencimento no mes SEGUINTE quando diaVencimento <= diaFechamento (ex.: fecha 25, vence 5). */
    @Test
    void novoParaCompetenciaComVencimentoAntesOuIgualAoFechamentoCaiNoMesSeguinte() {
        Cartao cartao = Cartao.novo(UUID.randomUUID(), null, "Itau", BigDecimal.TEN, 25, 5, null);
        ReflectionTestUtils.setField(cartao, "id", cartaoId);

        Fatura fatura = Fatura.novoParaCompetencia(cartao, YearMonth.of(2026, 8));

        assertThat(fatura.getDataFechamento()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(fatura.getDataVencimento()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    /** T2.1.2: dia ajustado para o ultimo valido do mes quando o mes e mais curto (ex.: dia 31 em fevereiro). */
    @Test
    void novoParaCompetenciaAjustaODiaParaOUltimoValidoDoMes() {
        Cartao cartao = Cartao.novo(UUID.randomUUID(), null, "Cartao", BigDecimal.TEN, 31, 31, null);
        ReflectionTestUtils.setField(cartao, "id", cartaoId);

        Fatura fatura = Fatura.novoParaCompetencia(cartao, YearMonth.of(2026, 2));

        assertThat(fatura.getDataFechamento()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void soUmaFaturaAtrasadaPodeSerParcelada() {
        Fatura fatura = novaFatura();
        assertThat(fatura.podeSerParcelada()).isFalse();

        fatura.fechar();
        assertThat(fatura.podeSerParcelada()).isFalse();

        fatura.marcarComoAtrasada();
        assertThat(fatura.podeSerParcelada()).isTrue();
    }
}
