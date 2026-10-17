package br.com.unifinancas.application.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.notificacao.SilencioNotificacaoVencimento;
import br.com.unifinancas.domain.notificacao.SilencioNotificacaoVencimentoRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SilencioNotificacaoVencimentoServiceTest {

    private final LancamentoRepository lancamentoRepository = mock(LancamentoRepository.class);
    private final SilencioNotificacaoVencimentoRepository silencioRepository =
            mock(SilencioNotificacaoVencimentoRepository.class);
    private final SilencioNotificacaoVencimentoService service =
            new SilencioNotificacaoVencimentoService(lancamentoRepository, silencioRepository);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID lancamentoId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);

    @Test
    void silenciarPersisteOSilencioQuandoAindaNaoExiste() {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), LocalDate.now(), LocalDate.now().plusDays(2), null, null, null, null, "Descrição de teste", null);
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamento));
        when(silencioRepository.existsByUsuarioIdAndLancamentoIdAndAmbienteFinanceiroId(usuarioId, lancamentoId, ambienteFinanceiroId))
                .thenReturn(false);

        service.silenciar(contexto, lancamentoId);

        ArgumentCaptor<SilencioNotificacaoVencimento> captor = ArgumentCaptor.forClass(SilencioNotificacaoVencimento.class);
        verify(silencioRepository).save(captor.capture());
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(usuarioId);
        assertThat(captor.getValue().getLancamentoId()).isEqualTo(lancamentoId);
        assertThat(captor.getValue().getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
    }

    @Test
    void silenciarEIdempotenteQuandoJaSilenciado() {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), LocalDate.now(), LocalDate.now().plusDays(2), null, null, null, null, "Descrição de teste", null);
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId))
                .thenReturn(Optional.of(lancamento));
        when(silencioRepository.existsByUsuarioIdAndLancamentoIdAndAmbienteFinanceiroId(usuarioId, lancamentoId, ambienteFinanceiroId))
                .thenReturn(true);

        service.silenciar(contexto, lancamentoId);

        verify(silencioRepository, never()).save(any());
    }

    @Test
    void silenciarLancamentoDeOutroAmbienteLancaRecursoNaoEncontrado() {
        when(lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.silenciar(contexto, lancamentoId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(silencioRepository, never()).save(any());
    }
}
