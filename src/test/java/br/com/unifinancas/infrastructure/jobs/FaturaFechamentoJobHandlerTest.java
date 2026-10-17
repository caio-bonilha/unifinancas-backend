package br.com.unifinancas.infrastructure.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.domain.cartao.Cartao;
import br.com.unifinancas.domain.cartao.CartaoRepository;
import br.com.unifinancas.domain.fatura.Fatura;
import br.com.unifinancas.domain.fatura.FaturaRepository;
import br.com.unifinancas.domain.fatura.StatusFatura;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class FaturaFechamentoJobHandlerTest {

    private final CartaoRepository cartaoRepository = mock(CartaoRepository.class);
    private final FaturaRepository faturaRepository = mock(FaturaRepository.class);
    private final FaturaFechamentoJobHandler handler = new FaturaFechamentoJobHandler(cartaoRepository, faturaRepository);

    private final UUID ambienteFinanceiroId = UUID.randomUUID();

    private Cartao cartaoComDiaDeFechamentoHoje() {
        Cartao cartao = Cartao.novo(ambienteFinanceiroId, null, "Nubank", BigDecimal.TEN,
                LocalDate.now().getDayOfMonth(), Math.min(LocalDate.now().getDayOfMonth() + 5, 28), null);
        ReflectionTestUtils.setField(cartao, "id", UUID.randomUUID());
        return cartao;
    }

    @Test
    void fechaAFaturaAbertaDeUmCartaoAtivoCujoDiaDeFechamentoChegouEAbreAProxima() {
        Cartao cartao = cartaoComDiaDeFechamentoHoje();
        Fatura faturaAtual = Fatura.novoParaCompetencia(cartao, YearMonth.now());
        when(cartaoRepository.findAll()).thenReturn(List.of(cartao));
        when(faturaRepository.findByCartaoIdAndStatus(cartao.getId(), StatusFatura.ABERTA)).thenReturn(Optional.of(faturaAtual));
        when(faturaRepository.findAllByStatusAndDataVencimentoBefore(StatusFatura.FECHADA, LocalDate.now())).thenReturn(List.of());

        handler.executar(null);

        assertThat(faturaAtual.getStatus()).isEqualTo(StatusFatura.FECHADA);
        ArgumentCaptor<Fatura> capturada = ArgumentCaptor.forClass(Fatura.class);
        verify(faturaRepository, times(2)).save(capturada.capture());
        Fatura proximaFatura = capturada.getAllValues().get(1);
        assertThat(proximaFatura.getCompetencia()).isEqualTo(YearMonth.now().plusMonths(1).atDay(1));
        assertThat(proximaFatura.getStatus()).isEqualTo(StatusFatura.ABERTA);
    }

    @Test
    void naoFechaUmCartaoInativo() {
        Cartao cartao = cartaoComDiaDeFechamentoHoje();
        cartao.desativar();
        when(cartaoRepository.findAll()).thenReturn(List.of(cartao));
        when(faturaRepository.findAllByStatusAndDataVencimentoBefore(StatusFatura.FECHADA, LocalDate.now())).thenReturn(List.of());

        handler.executar(null);

        verify(faturaRepository, never()).save(any());
        verify(faturaRepository, never()).findByCartaoIdAndStatus(any(), any());
    }

    @Test
    void naoFechaUmaFaturaCujaDataDeFechamentoAindaNaoChegou() {
        Cartao cartao = Cartao.novo(ambienteFinanceiroId, null, "Nubank", BigDecimal.TEN, 1, 10, null);
        ReflectionTestUtils.setField(cartao, "id", UUID.randomUUID());
        // competencia do mes seguinte - dataFechamento no futuro em relacao a hoje, na pratica
        Fatura faturaFutura = Fatura.novoParaCompetencia(cartao, YearMonth.now().plusMonths(2));
        when(cartaoRepository.findAll()).thenReturn(List.of(cartao));
        when(faturaRepository.findByCartaoIdAndStatus(cartao.getId(), StatusFatura.ABERTA)).thenReturn(Optional.of(faturaFutura));
        when(faturaRepository.findAllByStatusAndDataVencimentoBefore(StatusFatura.FECHADA, LocalDate.now())).thenReturn(List.of());

        handler.executar(null);

        assertThat(faturaFutura.getStatus()).isEqualTo(StatusFatura.ABERTA);
        verify(faturaRepository, never()).save(any());
    }

    @Test
    void marcaComoAtrasadaUmaFaturaFechadaVencidaSemPagamento() {
        Cartao cartao = Cartao.novo(ambienteFinanceiroId, null, "Nubank", BigDecimal.TEN, 10, 17, null);
        Fatura faturaVencida = Fatura.novoParaCompetencia(cartao, YearMonth.now().minusMonths(1));
        faturaVencida.fechar();
        when(cartaoRepository.findAll()).thenReturn(List.of());
        when(faturaRepository.findAllByStatusAndDataVencimentoBefore(StatusFatura.FECHADA, LocalDate.now()))
                .thenReturn(List.of(faturaVencida));

        handler.executar(null);

        assertThat(faturaVencida.getStatus()).isEqualTo(StatusFatura.ATRASADA);
        verify(faturaRepository).saveAll(List.of(faturaVencida));
    }
}
