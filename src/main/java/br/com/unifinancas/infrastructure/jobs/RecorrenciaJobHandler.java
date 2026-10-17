package br.com.unifinancas.infrastructure.jobs;

import br.com.unifinancas.application.lancamento.LancamentoService;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrente;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrenteRepository;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * T1.6.3, SAD Sec6.6 (RF-11): busca Lancamentos Recorrentes ativos cuja
 * proxima ocorrencia cai no dia corrente e materializa o Lancamento
 * correspondente (origem RECORRENCIA), reaproveitando {@link
 * LancamentoService} - "mesmo caminho de criacao usado pela API" (Sec6.6,
 * ponto 3), inclusive os eventos de dominio (orcamento, auditoria).
 * Idempotente por {@code (lancamentoRecorrenteId, dataVencimento)} (RNF-02):
 * uma reexecucao do job no mesmo dia (ex.: apos falha parcial) nao duplica
 * Lancamentos ja gerados.
 */
@Component
public class RecorrenciaJobHandler implements JobHandler {

    public static final String TIPO = "RECORRENCIA";

    private final LancamentoRecorrenteRepository lancamentoRecorrenteRepository;
    private final LancamentoRepository lancamentoRepository;
    private final LancamentoService lancamentoService;

    public RecorrenciaJobHandler(LancamentoRecorrenteRepository lancamentoRecorrenteRepository,
            LancamentoRepository lancamentoRepository, LancamentoService lancamentoService) {
        this.lancamentoRecorrenteRepository = lancamentoRecorrenteRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.lancamentoService = lancamentoService;
    }

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public void executar(JobExecucao job) {
        LocalDate hoje = LocalDate.now();
        List<LancamentoRecorrente> ativas = lancamentoRecorrenteRepository.findAllByAtivo(true);
        for (LancamentoRecorrente recorrente : ativas) {
            if (recorrente.ocorreEm(hoje)) {
                materializar(recorrente, hoje);
            }
        }
    }

    private void materializar(LancamentoRecorrente recorrente, LocalDate hoje) {
        boolean jaGerado = lancamentoRepository.existsByAmbienteFinanceiroIdAndLancamentoRecorrenteIdAndDataVencimento(
                recorrente.getAmbienteFinanceiroId(), recorrente.getId(), hoje);
        if (jaGerado) {
            return;
        }
        lancamentoService.criarDeRecorrencia(recorrente.getAmbienteFinanceiroId(), recorrente.getId(),
                recorrente.getTipo(), recorrente.getValor(), hoje, hoje, recorrente.getCategoriaId(),
                recorrente.getContaBancariaId(), recorrente.getDescricao(),
                "Gerado automaticamente pela recorrencia.");
    }
}
