package br.com.unifinancas.infrastructure.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.application.notificacao.NotificacaoService;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiroRepository;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.StatusVinculo;
import br.com.unifinancas.domain.ambientefinanceiro.TipoAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.StatusLancamento;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.notificacao.SilencioNotificacaoVencimento;
import br.com.unifinancas.domain.notificacao.SilencioNotificacaoVencimentoRepository;
import br.com.unifinancas.domain.notificacao.TipoNotificacao;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VencimentoLancamentoJobHandlerTest {

    private final LancamentoRepository lancamentoRepository = mock(LancamentoRepository.class);
    private final AmbienteFinanceiroRepository ambienteFinanceiroRepository = mock(AmbienteFinanceiroRepository.class);
    private final UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository =
            mock(UsuarioAmbienteFinanceiroRepository.class);
    private final NotificacaoService notificacaoService = mock(NotificacaoService.class);
    private final SilencioNotificacaoVencimentoRepository silencioNotificacaoVencimentoRepository =
            mock(SilencioNotificacaoVencimentoRepository.class);
    private final VencimentoLancamentoJobHandler handler = new VencimentoLancamentoJobHandler(lancamentoRepository,
            ambienteFinanceiroRepository, usuarioAmbienteFinanceiroRepository, notificacaoService,
            silencioNotificacaoVencimentoRepository);

    private final UUID ambienteId = UUID.randomUUID();
    private final LocalDate hoje = LocalDate.now();

    @Test
    void tipoCasaComOValorUsadoPeloJobExecucao() {
        assertThat(handler.tipo()).isEqualTo("VENCIMENTO_LANCAMENTO");
    }

    @Test
    void executarMarcaLancamentosVencidosComoAtrasado() {
        LancamentoFinanceiro vencido1 = LancamentoFinanceiro.novo(ambienteId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje.minusDays(5), hoje.minusDays(2), null, null, null, null, "Descrição de teste", null);
        LancamentoFinanceiro vencido2 = LancamentoFinanceiro.novo(ambienteId, TipoLancamento.DESPESA,
                new BigDecimal("30.00"), hoje.minusDays(3), hoje.minusDays(1), null, null, null, null, "Descrição de teste", null);
        when(lancamentoRepository.findAllByStatusAndDataVencimentoBefore(StatusLancamento.PENDENTE, hoje))
                .thenReturn(List.of(vencido1, vencido2));
        when(ambienteFinanceiroRepository.findAll()).thenReturn(List.of());

        handler.executar(JobExecucao.novo("VENCIMENTO_LANCAMENTO", "{}", Instant.now()));

        assertThat(vencido1.getStatus()).isEqualTo(StatusLancamento.ATRASADO);
        assertThat(vencido2.getStatus()).isEqualTo(StatusLancamento.ATRASADO);
        verify(lancamentoRepository).saveAll(List.of(vencido1, vencido2));
    }

    @Test
    void executarNotificaTodosOsMembrosAtivosParaLancamentoDentroDaJanela() {
        when(lancamentoRepository.findAllByStatusAndDataVencimentoBefore(StatusLancamento.PENDENTE, hoje)).thenReturn(List.of());
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Familia", TipoAmbienteFinanceiro.FAMILIAR, 3);
        ReflectionTestUtils.setField(ambiente, "id", ambienteId);
        when(ambienteFinanceiroRepository.findAll()).thenReturn(List.of(ambiente));

        LancamentoFinanceiro proximo = LancamentoFinanceiro.novo(ambienteId, TipoLancamento.DESPESA,
                new BigDecimal("100.00"), hoje, hoje.plusDays(2), null, null, null, null, "Descrição de teste", null);
        ReflectionTestUtils.setField(proximo, "id", UUID.randomUUID());
        when(lancamentoRepository.findAllByAmbienteFinanceiroIdAndStatusAndDataVencimentoBetween(
                ambienteId, StatusLancamento.PENDENTE, hoje, hoje.plusDays(3)))
                .thenReturn(List.of(proximo));

        UsuarioAmbienteFinanceiro membro1 = UsuarioAmbienteFinanceiro.vincularComPerfil(UUID.randomUUID(), ambienteId, PerfilUsuarioAmbiente.OWNER);
        UsuarioAmbienteFinanceiro membro2 = UsuarioAmbienteFinanceiro.vincularComPerfil(UUID.randomUUID(), ambienteId, PerfilUsuarioAmbiente.MEMBRO);
        when(usuarioAmbienteFinanceiroRepository.findAllByAmbienteFinanceiroIdAndStatus(ambienteId, StatusVinculo.ATIVO))
                .thenReturn(List.of(membro1, membro2));
        when(silencioNotificacaoVencimentoRepository.findAllByAmbienteFinanceiroIdAndLancamentoIdIn(
                eq(ambienteId), any())).thenReturn(List.of());

        handler.executar(JobExecucao.novo("VENCIMENTO_LANCAMENTO", "{}", Instant.now()));

        verify(notificacaoService).criar(eq(ambienteId), eq(membro1.getUsuarioId()), eq(TipoNotificacao.VENCIMENTO_CONTA),
                any(), any(), eq("lancamento"), eq(proximo.getId()), any());
        verify(notificacaoService).criar(eq(ambienteId), eq(membro2.getUsuarioId()), eq(TipoNotificacao.VENCIMENTO_CONTA),
                any(), any(), eq("lancamento"), eq(proximo.getId()), any());
    }

    @Test
    void executarNaoNotificaOUsuarioQueSilenciouOLancamento() {
        when(lancamentoRepository.findAllByStatusAndDataVencimentoBefore(StatusLancamento.PENDENTE, hoje)).thenReturn(List.of());
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Familia", TipoAmbienteFinanceiro.FAMILIAR, 3);
        ReflectionTestUtils.setField(ambiente, "id", ambienteId);
        when(ambienteFinanceiroRepository.findAll()).thenReturn(List.of(ambiente));

        LancamentoFinanceiro proximo = LancamentoFinanceiro.novo(ambienteId, TipoLancamento.DESPESA,
                new BigDecimal("100.00"), hoje, hoje.plusDays(2), null, null, null, null, "Descrição de teste", null);
        ReflectionTestUtils.setField(proximo, "id", UUID.randomUUID());
        when(lancamentoRepository.findAllByAmbienteFinanceiroIdAndStatusAndDataVencimentoBetween(
                ambienteId, StatusLancamento.PENDENTE, hoje, hoje.plusDays(3)))
                .thenReturn(List.of(proximo));

        UsuarioAmbienteFinanceiro membro1 = UsuarioAmbienteFinanceiro.vincularComPerfil(UUID.randomUUID(), ambienteId, PerfilUsuarioAmbiente.OWNER);
        UsuarioAmbienteFinanceiro membro2 = UsuarioAmbienteFinanceiro.vincularComPerfil(UUID.randomUUID(), ambienteId, PerfilUsuarioAmbiente.MEMBRO);
        when(usuarioAmbienteFinanceiroRepository.findAllByAmbienteFinanceiroIdAndStatus(ambienteId, StatusVinculo.ATIVO))
                .thenReturn(List.of(membro1, membro2));

        SilencioNotificacaoVencimento silencioDoMembro1 =
                SilencioNotificacaoVencimento.novo(ambienteId, membro1.getUsuarioId(), proximo.getId());
        when(silencioNotificacaoVencimentoRepository.findAllByAmbienteFinanceiroIdAndLancamentoIdIn(
                eq(ambienteId), any())).thenReturn(List.of(silencioDoMembro1));

        handler.executar(JobExecucao.novo("VENCIMENTO_LANCAMENTO", "{}", Instant.now()));

        verify(notificacaoService, never()).criar(eq(ambienteId), eq(membro1.getUsuarioId()), any(), any(), any(), any(), any(), any());
        verify(notificacaoService).criar(eq(ambienteId), eq(membro2.getUsuarioId()), eq(TipoNotificacao.VENCIMENTO_CONTA),
                any(), any(), eq("lancamento"), eq(proximo.getId()), any());
    }

    @Test
    void executarSemLancamentosProximosNaoNotificaNinguem() {
        when(lancamentoRepository.findAllByStatusAndDataVencimentoBefore(StatusLancamento.PENDENTE, hoje)).thenReturn(List.of());
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Familia", TipoAmbienteFinanceiro.FAMILIAR, 3);
        ReflectionTestUtils.setField(ambiente, "id", ambienteId);
        when(ambienteFinanceiroRepository.findAll()).thenReturn(List.of(ambiente));
        when(lancamentoRepository.findAllByAmbienteFinanceiroIdAndStatusAndDataVencimentoBetween(
                ambienteId, StatusLancamento.PENDENTE, hoje, hoje.plusDays(3)))
                .thenReturn(List.of());

        handler.executar(JobExecucao.novo("VENCIMENTO_LANCAMENTO", "{}", Instant.now()));

        verify(usuarioAmbienteFinanceiroRepository, never()).findAllByAmbienteFinanceiroIdAndStatus(any(), any());
        verify(notificacaoService, never()).criar(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
