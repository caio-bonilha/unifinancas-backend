package br.com.unifinancas.domain.orcamentometa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrcamentoMetaTest {

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID categoriaId = UUID.randomUUID();
    private final UUID contaBancariaId = UUID.randomUUID();
    private final LocalDate hoje = LocalDate.now();

    @Test
    void novoOrcamentoNasceAtivoComPeriodoMensalETipoOrcamentoELimiarPadrao() {
        OrcamentoMeta orcamento = OrcamentoMeta.novoOrcamento(ambienteFinanceiroId, categoriaId,
                new BigDecimal("500.00"), null, hoje, hoje.plusMonths(1));

        assertThat(orcamento.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(orcamento.getTipo()).isEqualTo(TipoOrcamentoMeta.ORCAMENTO);
        assertThat(orcamento.getCategoriaId()).isEqualTo(categoriaId);
        assertThat(orcamento.getValorLimite()).isEqualByComparingTo("500.00");
        assertThat(orcamento.getValorAlvo()).isNull();
        assertThat(orcamento.getNome()).isNull();
        assertThat(orcamento.getContaBancariaId()).isNull();
        assertThat(orcamento.getPeriodo()).isEqualTo(PeriodoOrcamentoMeta.MENSAL);
        assertThat(orcamento.getStatus()).isEqualTo(StatusOrcamentoMeta.ATIVO);
        assertThat(orcamento.getLimiarAlertaPercentual()).isEqualByComparingTo("80");
        assertThat(orcamento.getUltimoAlertaTipo()).isNull();
        assertThat(orcamento.getUltimoAlertaPeriodo()).isNull();
        assertThat(orcamento.getCriadoEm()).isNotNull();
        assertThat(orcamento.getAtualizadoEm()).isNotNull();
    }

    @Test
    void novoOrcamentoComLimiarAlertaCustomizadoUsaOValorInformado() {
        OrcamentoMeta orcamento = OrcamentoMeta.novoOrcamento(ambienteFinanceiroId, categoriaId,
                new BigDecimal("500.00"), new BigDecimal("70"), hoje, null);

        assertThat(orcamento.getLimiarAlertaPercentual()).isEqualByComparingTo("70");
    }

    @Test
    void novoOrcamentoComLimiarAlertaForaDoIntervaloLancaRegraDeNegocio() {
        assertThatThrownBy(() -> OrcamentoMeta.novoOrcamento(ambienteFinanceiroId, categoriaId,
                new BigDecimal("500.00"), BigDecimal.ZERO, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("LIMIAR_ALERTA_INVALIDO"));
        assertThatThrownBy(() -> OrcamentoMeta.novoOrcamento(ambienteFinanceiroId, categoriaId,
                new BigDecimal("500.00"), new BigDecimal("101"), hoje, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("LIMIAR_ALERTA_INVALIDO"));
    }

    @Test
    void orcamentoSemCategoriaLancaRegraDeNegocio() {
        assertThatThrownBy(() -> OrcamentoMeta.novoOrcamento(ambienteFinanceiroId, null, new BigDecimal("500.00"),
                null, hoje, hoje.plusMonths(1)))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo())
                        .isEqualTo("CATEGORIA_OBRIGATORIA_PARA_ORCAMENTO"));
    }

    @Test
    void orcamentoSemValorLimiteLancaRegraDeNegocio() {
        assertThatThrownBy(() -> OrcamentoMeta.novoOrcamento(ambienteFinanceiroId, categoriaId, null, null, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo())
                        .isEqualTo("VALOR_LIMITE_OBRIGATORIO_PARA_ORCAMENTO"));
    }

    @Test
    void orcamentoComValorLimiteZeroOuNegativoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> OrcamentoMeta.novoOrcamento(ambienteFinanceiroId, categoriaId, BigDecimal.ZERO, null, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> OrcamentoMeta.novoOrcamento(
                ambienteFinanceiroId, categoriaId, new BigDecimal("-10.00"), null, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void jaAlertouNestePeriodoSoRetornaVerdadeiroParaMesmaSeveridadeEMesmoPeriodo() {
        OrcamentoMeta orcamento = OrcamentoMeta.novoOrcamento(ambienteFinanceiroId, categoriaId,
                new BigDecimal("500.00"), null, hoje, null);
        LocalDate inicioMes = hoje.withDayOfMonth(1);

        assertThat(orcamento.jaAlertouNestePeriodo(SeveridadeAlertaOrcamento.AVISO, inicioMes)).isFalse();

        orcamento.registrarAlertaEnviado(SeveridadeAlertaOrcamento.AVISO, inicioMes);

        assertThat(orcamento.jaAlertouNestePeriodo(SeveridadeAlertaOrcamento.AVISO, inicioMes)).isTrue();
        assertThat(orcamento.jaAlertouNestePeriodo(SeveridadeAlertaOrcamento.ESTOURADO, inicioMes)).isFalse();
        assertThat(orcamento.jaAlertouNestePeriodo(SeveridadeAlertaOrcamento.AVISO, inicioMes.plusMonths(1))).isFalse();
    }

    @Test
    void novaMetaNasceAtivaComPeriodoMensalETipoMeta() {
        OrcamentoMeta meta = OrcamentoMeta.novaMeta(ambienteFinanceiroId, "Viagem", new BigDecimal("3000.00"),
                contaBancariaId, hoje, hoje.plusYears(1));

        assertThat(meta.getTipo()).isEqualTo(TipoOrcamentoMeta.META);
        assertThat(meta.getNome()).isEqualTo("Viagem");
        assertThat(meta.getValorAlvo()).isEqualByComparingTo("3000.00");
        assertThat(meta.getContaBancariaId()).isEqualTo(contaBancariaId);
        assertThat(meta.getCategoriaId()).isNull();
        assertThat(meta.getValorLimite()).isNull();
        assertThat(meta.getPeriodo()).isEqualTo(PeriodoOrcamentoMeta.MENSAL);
        assertThat(meta.getStatus()).isEqualTo(StatusOrcamentoMeta.ATIVO);
    }

    @Test
    void metaSemContaBancariaOuNomeFuncionaNormalmente() {
        OrcamentoMeta meta = OrcamentoMeta.novaMeta(ambienteFinanceiroId, null, new BigDecimal("100.00"), null, null, null);

        assertThat(meta.getNome()).isNull();
        assertThat(meta.getContaBancariaId()).isNull();
    }

    @Test
    void metaSemValorAlvoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> OrcamentoMeta.novaMeta(ambienteFinanceiroId, "Viagem", null, contaBancariaId, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo())
                        .isEqualTo("VALOR_ALVO_OBRIGATORIO_PARA_META"));
    }

    @Test
    void metaComValorAlvoZeroOuNegativoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> OrcamentoMeta.novaMeta(ambienteFinanceiroId, "Viagem", BigDecimal.ZERO, null, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> OrcamentoMeta.novaMeta(
                ambienteFinanceiroId, "Viagem", new BigDecimal("-1.00"), null, hoje, null))
                .isInstanceOf(RegraDeNegocioException.class);
    }
}
