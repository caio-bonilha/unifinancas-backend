package br.com.unifinancas.infrastructure.jobs;

import br.com.unifinancas.domain.cartao.Cartao;
import br.com.unifinancas.domain.cartao.CartaoRepository;
import br.com.unifinancas.domain.fatura.Fatura;
import br.com.unifinancas.domain.fatura.FaturaRepository;
import br.com.unifinancas.domain.fatura.StatusFatura;
import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * T2.1.2, SAD Sec6.5: job diario de Fatura - (1) fecha a Fatura ABERTA de
 * cada Cartao ativo cujo {@code dataFechamento} ja chegou, abrindo em
 * seguida a Fatura da competencia seguinte (para novas compras terem onde
 * cair); (2) marca ATRASADA toda Fatura FECHADA cujo {@code dataVencimento}
 * ja passou sem pagamento algum. Varre TODOS os Ambientes - job de sistema
 * (Sec12.6), mesmo criterio de {@code VencimentoLancamentoJobHandler}.
 */
@Component
public class FaturaFechamentoJobHandler implements JobHandler {

    public static final String TIPO = "FECHAMENTO_FATURA";

    private final CartaoRepository cartaoRepository;
    private final FaturaRepository faturaRepository;

    public FaturaFechamentoJobHandler(CartaoRepository cartaoRepository, FaturaRepository faturaRepository) {
        this.cartaoRepository = cartaoRepository;
        this.faturaRepository = faturaRepository;
    }

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public void executar(JobExecucao job) {
        fecharFaturasDoDia();
        marcarFaturasVencidasComoAtrasada();
    }

    private void fecharFaturasDoDia() {
        LocalDate hoje = LocalDate.now();
        for (Cartao cartao : cartaoRepository.findAll()) {
            if (!cartao.isAtivo()) {
                continue;
            }
            Optional<Fatura> faturaAberta = faturaRepository.findByCartaoIdAndStatus(cartao.getId(), StatusFatura.ABERTA);
            faturaAberta.filter(fatura -> !hoje.isBefore(fatura.getDataFechamento()))
                    .ifPresent(fatura -> fechar(cartao, fatura));
        }
    }

    private void fechar(Cartao cartao, Fatura fatura) {
        fatura.fechar();
        faturaRepository.save(fatura);

        YearMonth proximaCompetencia = YearMonth.from(fatura.getCompetencia()).plusMonths(1);
        faturaRepository.save(Fatura.novoParaCompetencia(cartao, proximaCompetencia));
    }

    private void marcarFaturasVencidasComoAtrasada() {
        List<Fatura> vencidas = faturaRepository.findAllByStatusAndDataVencimentoBefore(StatusFatura.FECHADA, LocalDate.now());
        vencidas.forEach(Fatura::marcarComoAtrasada);
        faturaRepository.saveAll(vencidas);
    }
}
