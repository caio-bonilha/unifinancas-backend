package br.com.unifinancas.application.notificacao;

import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.notificacao.SilencioNotificacaoVencimento;
import br.com.unifinancas.domain.notificacao.SilencioNotificacaoVencimentoRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T1.5.7 (decisao consultada com o usuario): permite que cada usuario
 * silencie, individualmente, o aviso recorrente de vencimento proximo
 * (Sec6.5/T1.5.4) de um Lancamento especifico, sem afetar os demais
 * membros do Ambiente Financeiro.
 */
@Service
public class SilencioNotificacaoVencimentoService {

    private final LancamentoRepository lancamentoRepository;
    private final SilencioNotificacaoVencimentoRepository silencioRepository;

    public SilencioNotificacaoVencimentoService(LancamentoRepository lancamentoRepository,
            SilencioNotificacaoVencimentoRepository silencioRepository) {
        this.lancamentoRepository = lancamentoRepository;
        this.silencioRepository = silencioRepository;
    }

    @Transactional
    public void silenciar(TenantContext contexto, UUID lancamentoId) {
        lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "LANCAMENTO_NAO_ENCONTRADO", "Lancamento nao encontrado neste Ambiente Financeiro."));

        boolean jaSilenciado = silencioRepository.existsByUsuarioIdAndLancamentoIdAndAmbienteFinanceiroId(
                contexto.usuarioId(), lancamentoId, contexto.ambienteFinanceiroId());
        if (!jaSilenciado) {
            silencioRepository.save(
                    SilencioNotificacaoVencimento.novo(contexto.ambienteFinanceiroId(), contexto.usuarioId(), lancamentoId));
        }
    }
}
