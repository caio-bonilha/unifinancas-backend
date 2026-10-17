package br.com.unifinancas.infrastructure.jobs;

import br.com.unifinancas.application.notificacao.NotificacaoService;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiroRepository;
import br.com.unifinancas.domain.ambientefinanceiro.StatusVinculo;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.StatusLancamento;
import br.com.unifinancas.domain.notificacao.CanalNotificacao;
import br.com.unifinancas.domain.notificacao.SilencioNotificacaoVencimentoRepository;
import br.com.unifinancas.domain.notificacao.TipoNotificacao;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * T1.5.4, SAD Sec6.5: job diario de vencimento - (1) marca ATRASADO todo
 * Lancamento PENDENTE cuja data_vencimento ja passou; (2) notifica
 * "vencimento proximo" para Lancamentos PENDENTE dentro da janela de
 * antecedencia configurada por Ambiente Financeiro (Sec4.16, decisao
 * consultada com o usuario). Varre TODOS os ambientes - e um job de
 * sistema (Sec12.6), sem TenantContext de usuario.
 *
 * <p>Notifica a cada execucao do job enquanto o Lancamento estiver dentro
 * da janela (decisao consultada com o usuario - sem deduplicacao, ao
 * contrario do alerta de Orcamento da T1.5.3), exceto para o usuario que
 * silenciou aquele Lancamento especifico (T1.5.7).
 */
@Component
public class VencimentoLancamentoJobHandler implements JobHandler {

    public static final String TIPO = "VENCIMENTO_LANCAMENTO";

    private final LancamentoRepository lancamentoRepository;
    private final AmbienteFinanceiroRepository ambienteFinanceiroRepository;
    private final UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository;
    private final NotificacaoService notificacaoService;
    private final SilencioNotificacaoVencimentoRepository silencioNotificacaoVencimentoRepository;

    public VencimentoLancamentoJobHandler(LancamentoRepository lancamentoRepository,
            AmbienteFinanceiroRepository ambienteFinanceiroRepository,
            UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository,
            NotificacaoService notificacaoService,
            SilencioNotificacaoVencimentoRepository silencioNotificacaoVencimentoRepository) {
        this.lancamentoRepository = lancamentoRepository;
        this.ambienteFinanceiroRepository = ambienteFinanceiroRepository;
        this.usuarioAmbienteFinanceiroRepository = usuarioAmbienteFinanceiroRepository;
        this.notificacaoService = notificacaoService;
        this.silencioNotificacaoVencimentoRepository = silencioNotificacaoVencimentoRepository;
    }

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public void executar(JobExecucao job) {
        marcarVencidosComoAtrasado();
        notificarVencimentosProximos();
    }

    private void marcarVencidosComoAtrasado() {
        List<LancamentoFinanceiro> vencidos =
                lancamentoRepository.findAllByStatusAndDataVencimentoBefore(StatusLancamento.PENDENTE, LocalDate.now());
        vencidos.forEach(LancamentoFinanceiro::marcarComoAtrasado);
        lancamentoRepository.saveAll(vencidos);
    }

    private void notificarVencimentosProximos() {
        for (AmbienteFinanceiro ambiente : ambienteFinanceiroRepository.findAll()) {
            LocalDate hoje = LocalDate.now();
            LocalDate limite = hoje.plusDays(ambiente.getDiasAntecedenciaNotificacaoVencimento());

            List<LancamentoFinanceiro> proximos = lancamentoRepository.findAllByAmbienteFinanceiroIdAndStatusAndDataVencimentoBetween(
                    ambiente.getId(), StatusLancamento.PENDENTE, hoje, limite);
            if (proximos.isEmpty()) {
                continue;
            }

            List<UsuarioAmbienteFinanceiro> membros =
                    usuarioAmbienteFinanceiroRepository.findAllByAmbienteFinanceiroIdAndStatus(ambiente.getId(), StatusVinculo.ATIVO);
            Set<SilencioChave> silenciados = buscarSilenciados(ambiente.getId(), proximos);
            for (LancamentoFinanceiro lancamento : proximos) {
                notificarMembros(ambiente, lancamento, membros, silenciados);
            }
        }
    }

    private Set<SilencioChave> buscarSilenciados(UUID ambienteId, List<LancamentoFinanceiro> proximos) {
        List<UUID> lancamentoIds = proximos.stream().map(LancamentoFinanceiro::getId).toList();
        return silencioNotificacaoVencimentoRepository
                .findAllByAmbienteFinanceiroIdAndLancamentoIdIn(ambienteId, lancamentoIds).stream()
                .map(s -> new SilencioChave(s.getUsuarioId(), s.getLancamentoId()))
                .collect(Collectors.toSet());
    }

    private void notificarMembros(AmbienteFinanceiro ambiente, LancamentoFinanceiro lancamento,
            List<UsuarioAmbienteFinanceiro> membros, Set<SilencioChave> silenciados) {
        String titulo = "Lancamento proximo do vencimento";
        String mensagem = String.format(
                "Um lancamento de R$ %s vence em %s.", lancamento.getValor(), lancamento.getDataVencimento());

        for (UsuarioAmbienteFinanceiro membro : membros) {
            if (silenciados.contains(new SilencioChave(membro.getUsuarioId(), lancamento.getId()))) {
                continue;
            }
            notificacaoService.criar(ambiente.getId(), membro.getUsuarioId(), TipoNotificacao.VENCIMENTO_CONTA, titulo,
                    mensagem, "lancamento", lancamento.getId(), Set.of(CanalNotificacao.PUSH, CanalNotificacao.EMAIL));
        }
    }

    private record SilencioChave(UUID usuarioId, UUID lancamentoId) {
    }
}
