package br.com.unifinancas.domain.lancamentorecorrente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LancamentoRecorrenteTest {

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID categoriaId = UUID.randomUUID();
    private final UUID contaBancariaId = UUID.randomUUID();
    private final LocalDate hoje = LocalDate.now();

    @Test
    void novaRecorrenciaDespesaNasceAtivaComTodosOsCamposPreenchidos() {
        LancamentoRecorrente recorrente = LancamentoRecorrente.novo(ambienteFinanceiroId, "Aluguel", TipoLancamento.DESPESA,
                new BigDecimal("1500.00"), categoriaId, contaBancariaId, FrequenciaRecorrencia.MENSAL, 5, hoje, null);

        assertThat(recorrente.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(recorrente.getDescricao()).isEqualTo("Aluguel");
        assertThat(recorrente.getTipo()).isEqualTo(TipoLancamento.DESPESA);
        assertThat(recorrente.getValor()).isEqualByComparingTo("1500.00");
        assertThat(recorrente.getCategoriaId()).isEqualTo(categoriaId);
        assertThat(recorrente.getContaBancariaId()).isEqualTo(contaBancariaId);
        assertThat(recorrente.getFrequencia()).isEqualTo(FrequenciaRecorrencia.MENSAL);
        assertThat(recorrente.getDiaReferencia()).isEqualTo(5);
        assertThat(recorrente.getDataInicio()).isEqualTo(hoje);
        assertThat(recorrente.getDataFim()).isNull();
        assertThat(recorrente.isAtivo()).isTrue();
        assertThat(recorrente.getCriadoEm()).isNotNull();
        assertThat(recorrente.getAtualizadoEm()).isNotNull();
    }

    @Test
    void descricaoEmBrancoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> LancamentoRecorrente.novo(ambienteFinanceiroId, "  ", TipoLancamento.DESPESA,
                new BigDecimal("10.00"), null, contaBancariaId, FrequenciaRecorrencia.MENSAL, 1, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DESCRICAO_OBRIGATORIA"));
    }

    @Test
    void valorZeroOuNegativoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> LancamentoRecorrente.novo(ambienteFinanceiroId, "Aluguel", TipoLancamento.DESPESA,
                BigDecimal.ZERO, null, contaBancariaId, FrequenciaRecorrencia.MENSAL, 1, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("VALOR_INVALIDO"));

        assertThatThrownBy(() -> LancamentoRecorrente.novo(ambienteFinanceiroId, "Aluguel", TipoLancamento.DESPESA,
                new BigDecimal("-5.00"), null, contaBancariaId, FrequenciaRecorrencia.MENSAL, 1, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("VALOR_INVALIDO"));
    }

    @Test
    void dataFimAnteriorADataInicioLancaRegraDeNegocio() {
        assertThatThrownBy(() -> LancamentoRecorrente.novo(ambienteFinanceiroId, "Aluguel", TipoLancamento.DESPESA,
                new BigDecimal("1500.00"), null, contaBancariaId, FrequenciaRecorrencia.MENSAL, 5, hoje,
                hoje.minusDays(1)))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("PERIODO_INVALIDO"));
    }

    @Test
    void dataFimIgualOuPosteriorADataInicioFunciona() {
        LancamentoRecorrente recorrente = LancamentoRecorrente.novo(ambienteFinanceiroId, "Assinatura", TipoLancamento.DESPESA,
                new BigDecimal("39.90"), null, contaBancariaId, FrequenciaRecorrencia.MENSAL, 10, hoje, hoje);

        assertThat(recorrente.getDataFim()).isEqualTo(hoje);
    }

    @Test
    void editarAtualizaOsCamposDescritivosMasNuncaOTipo() {
        LancamentoRecorrente recorrente = LancamentoRecorrente.novo(ambienteFinanceiroId, "Aluguel", TipoLancamento.DESPESA,
                new BigDecimal("1500.00"), categoriaId, contaBancariaId, FrequenciaRecorrencia.MENSAL, 5, hoje, null);
        UUID novaCategoria = UUID.randomUUID();

        recorrente.editar("Aluguel reajustado", new BigDecimal("1600.00"), novaCategoria, contaBancariaId,
                FrequenciaRecorrencia.MENSAL, 10, hoje.plusDays(1), hoje.plusMonths(6));

        assertThat(recorrente.getDescricao()).isEqualTo("Aluguel reajustado");
        assertThat(recorrente.getValor()).isEqualByComparingTo("1600.00");
        assertThat(recorrente.getCategoriaId()).isEqualTo(novaCategoria);
        assertThat(recorrente.getDiaReferencia()).isEqualTo(10);
        assertThat(recorrente.getDataInicio()).isEqualTo(hoje.plusDays(1));
        assertThat(recorrente.getDataFim()).isEqualTo(hoje.plusMonths(6));
        assertThat(recorrente.getTipo()).isEqualTo(TipoLancamento.DESPESA);
    }

    @Test
    void editarComValorInvalidoLancaRegraDeNegocio() {
        LancamentoRecorrente recorrente = LancamentoRecorrente.novo(ambienteFinanceiroId, "Aluguel", TipoLancamento.DESPESA,
                new BigDecimal("1500.00"), null, contaBancariaId, FrequenciaRecorrencia.MENSAL, 5, hoje, null);

        assertThatThrownBy(() -> recorrente.editar("Aluguel", BigDecimal.ZERO, null, contaBancariaId,
                FrequenciaRecorrencia.MENSAL, 5, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("VALOR_INVALIDO"));
    }

    @Test
    void desativarEReativarAlternamAtivoEIdempotentes() {
        LancamentoRecorrente recorrente = LancamentoRecorrente.novo(ambienteFinanceiroId, "Aluguel", TipoLancamento.DESPESA,
                new BigDecimal("1500.00"), null, contaBancariaId, FrequenciaRecorrencia.MENSAL, 5, hoje, null);

        recorrente.desativar();
        assertThat(recorrente.isAtivo()).isFalse();
        recorrente.desativar();
        assertThat(recorrente.isAtivo()).isFalse();

        recorrente.reativar();
        assertThat(recorrente.isAtivo()).isTrue();
        recorrente.reativar();
        assertThat(recorrente.isAtivo()).isTrue();
    }

    @Test
    void semanalSemDiaReferenciaOuForaDoIntervaloLancaRegraDeNegocio() {
        assertThatThrownBy(() -> LancamentoRecorrente.novo(ambienteFinanceiroId, "Academia", TipoLancamento.DESPESA,
                new BigDecimal("50.00"), null, contaBancariaId, FrequenciaRecorrencia.SEMANAL, null, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DIA_REFERENCIA_INVALIDO"));

        assertThatThrownBy(() -> LancamentoRecorrente.novo(ambienteFinanceiroId, "Academia", TipoLancamento.DESPESA,
                new BigDecimal("50.00"), null, contaBancariaId, FrequenciaRecorrencia.SEMANAL, 8, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DIA_REFERENCIA_INVALIDO"));
    }

    @Test
    void mensalSemDiaReferenciaOuForaDoIntervaloLancaRegraDeNegocio() {
        assertThatThrownBy(() -> LancamentoRecorrente.novo(ambienteFinanceiroId, "Aluguel", TipoLancamento.DESPESA,
                new BigDecimal("1500.00"), null, contaBancariaId, FrequenciaRecorrencia.MENSAL, null, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DIA_REFERENCIA_INVALIDO"));

        assertThatThrownBy(() -> LancamentoRecorrente.novo(ambienteFinanceiroId, "Aluguel", TipoLancamento.DESPESA,
                new BigDecimal("1500.00"), null, contaBancariaId, FrequenciaRecorrencia.MENSAL, 32, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DIA_REFERENCIA_INVALIDO"));
    }

    @Test
    void diariaEAnualNaoExigemDiaReferencia() {
        LancamentoRecorrente diaria = LancamentoRecorrente.novo(ambienteFinanceiroId, "Cafe", TipoLancamento.DESPESA,
                new BigDecimal("10.00"), null, contaBancariaId, FrequenciaRecorrencia.DIARIA, null, hoje, null);
        LancamentoRecorrente anual = LancamentoRecorrente.novo(ambienteFinanceiroId, "IPVA", TipoLancamento.DESPESA,
                new BigDecimal("800.00"), null, contaBancariaId, FrequenciaRecorrencia.ANUAL, null, hoje, null);

        assertThat(diaria.getDiaReferencia()).isNull();
        assertThat(anual.getDiaReferencia()).isNull();
    }

    @Test
    void ocorreEmForaDoPeriodoDaRecorrenciaRetornaFalso() {
        LancamentoRecorrente recorrente = LancamentoRecorrente.novo(ambienteFinanceiroId, "Aluguel", TipoLancamento.DESPESA,
                new BigDecimal("1500.00"), null, contaBancariaId, FrequenciaRecorrencia.DIARIA, null,
                hoje, hoje.plusDays(10));

        assertThat(recorrente.ocorreEm(hoje.minusDays(1))).isFalse();
        assertThat(recorrente.ocorreEm(hoje.plusDays(11))).isFalse();
        assertThat(recorrente.ocorreEm(hoje)).isTrue();
        assertThat(recorrente.ocorreEm(hoje.plusDays(10))).isTrue();
    }

    @Test
    void ocorreEmSemanalSoNoDiaDaSemanaConfigurado() {
        LocalDate segunda = LocalDate.of(2026, 8, 24);
        LocalDate terca = segunda.plusDays(1);
        LancamentoRecorrente recorrente = LancamentoRecorrente.novo(ambienteFinanceiroId, "Feira", TipoLancamento.DESPESA,
                new BigDecimal("80.00"), null, contaBancariaId, FrequenciaRecorrencia.SEMANAL, 1, segunda, null);

        assertThat(recorrente.ocorreEm(segunda)).isTrue();
        assertThat(recorrente.ocorreEm(segunda.plusWeeks(3))).isTrue();
        assertThat(recorrente.ocorreEm(terca)).isFalse();
    }

    @Test
    void ocorreEmMensalAjustaParaOUltimoDiaValidoDoMes() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LancamentoRecorrente recorrente = LancamentoRecorrente.novo(ambienteFinanceiroId, "Assinatura", TipoLancamento.DESPESA,
                new BigDecimal("39.90"), null, contaBancariaId, FrequenciaRecorrencia.MENSAL, 31, inicio, null);

        assertThat(recorrente.ocorreEm(LocalDate.of(2026, 1, 31))).isTrue();
        assertThat(recorrente.ocorreEm(LocalDate.of(2026, 2, 28))).isTrue();
        assertThat(recorrente.ocorreEm(LocalDate.of(2026, 4, 30))).isTrue();
        assertThat(recorrente.ocorreEm(LocalDate.of(2026, 4, 29))).isFalse();
    }

    @Test
    void ocorreEmAnualNoAniversarioDeDataInicioAjustaAno29DeFevereiro() {
        LocalDate inicio = LocalDate.of(2024, 2, 29);
        LancamentoRecorrente recorrente = LancamentoRecorrente.novo(ambienteFinanceiroId, "IPVA", TipoLancamento.DESPESA,
                new BigDecimal("800.00"), null, contaBancariaId, FrequenciaRecorrencia.ANUAL, null, inicio, null);

        assertThat(recorrente.ocorreEm(LocalDate.of(2025, 2, 28))).isTrue();
        assertThat(recorrente.ocorreEm(LocalDate.of(2028, 2, 29))).isTrue();
        assertThat(recorrente.ocorreEm(LocalDate.of(2025, 3, 1))).isFalse();
    }
}
