package br.com.unifinancas.domain.lancamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LancamentoFinanceiroTest {

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID categoriaId = UUID.randomUUID();
    private final UUID contatoId = UUID.randomUUID();
    private final UUID contaBancariaId = UUID.randomUUID();
    private final LocalDate hoje = LocalDate.now();

    @Test
    void novoLancamentoDespesaNasceComOrigemManualEStatusPendente() {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("150.00"), hoje, hoje.plusDays(5), categoriaId, contatoId, contaBancariaId,
                FormaPagamento.PIX, "Descrição de teste", "Conta de luz");

        assertThat(lancamento.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(lancamento.getTipo()).isEqualTo(TipoLancamento.DESPESA);
        assertThat(lancamento.getStatus()).isEqualTo(StatusLancamento.PENDENTE);
        assertThat(lancamento.getValor()).isEqualByComparingTo("150.00");
        assertThat(lancamento.getOrigem()).isEqualTo(OrigemLancamento.MANUAL);
        assertThat(lancamento.getDescricao()).isEqualTo("Descrição de teste");
        assertThat(lancamento.getObservacao()).isEqualTo("Conta de luz");
        assertThat(lancamento.getCriadoEm()).isNotNull();
        assertThat(lancamento.getAtualizadoEm()).isNotNull();
    }

    @Test
    void valorZeroLancaRegraDeNegocio() {
        assertThatThrownBy(() -> LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                BigDecimal.ZERO, hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("VALOR_INVALIDO"));
    }

    @Test
    void valorNegativoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("-10.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("VALOR_INVALIDO"));
    }

    @Test
    void descricaoNulaLancaRegraDeNegocio() {
        assertThatThrownBy(() -> LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, null, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DESCRICAO_OBRIGATORIA"));
    }

    @Test
    void descricaoEmBrancoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "   ", null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DESCRICAO_OBRIGATORIA"));
    }

    @Test
    void rn06_despesaPendenteEContaAPagarMasNaoAReceber() {
        LancamentoFinanceiro despesa = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);

        assertThat(despesa.ehContaAPagar()).isTrue();
        assertThat(despesa.ehContaAReceber()).isFalse();
    }

    @Test
    void rn06_receitaPendenteEContaAReceberMasNaoAPagar() {
        LancamentoFinanceiro receita = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.RECEITA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);

        assertThat(receita.ehContaAReceber()).isTrue();
        assertThat(receita.ehContaAPagar()).isFalse();
    }

    @Test
    void editarAtualizaOsCamposDescritivosEMantemOTipoEOStatus() {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, categoriaId, contatoId, contaBancariaId, FormaPagamento.PIX,
                "descricao original", "original");

        UUID novaCategoria = UUID.randomUUID();
        lancamento.editar(new BigDecimal("75.00"), hoje.minusDays(1), hoje.plusDays(10), novaCategoria, null,
                contaBancariaId, FormaPagamento.BOLETO, "descricao editada", "editado");

        assertThat(lancamento.getValor()).isEqualByComparingTo("75.00");
        assertThat(lancamento.getDataVencimento()).isEqualTo(hoje.plusDays(10));
        assertThat(lancamento.getCategoriaId()).isEqualTo(novaCategoria);
        assertThat(lancamento.getContatoId()).isNull();
        assertThat(lancamento.getFormaPagamento()).isEqualTo(FormaPagamento.BOLETO);
        assertThat(lancamento.getDescricao()).isEqualTo("descricao editada");
        assertThat(lancamento.getObservacao()).isEqualTo("editado");
        assertThat(lancamento.getTipo()).isEqualTo(TipoLancamento.DESPESA);
        assertThat(lancamento.getStatus()).isEqualTo(StatusLancamento.PENDENTE);
    }

    @Test
    void editarComDescricaoEmBrancoLancaRegraDeNegocio() {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);

        assertThatThrownBy(() -> lancamento.editar(new BigDecimal("60.00"), hoje, hoje, null, null, contaBancariaId,
                null, "  ", null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DESCRICAO_OBRIGATORIA"));
    }

    @Test
    void editarUmLancamentoJaPagoLancaRegraDeNegocio() {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);
        lancamento.confirmarPagamento(hoje, contaBancariaId, FormaPagamento.PIX);

        assertThatThrownBy(() -> lancamento.editar(new BigDecimal("60.00"), hoje, hoje, null, null, contaBancariaId,
                null, "Descrição de teste", null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("LANCAMENTO_NAO_EDITAVEL"));
    }

    @Test
    void confirmarPagamentoEmDespesaPendenteMudaParaPagoComDataDePagamento() {
        LancamentoFinanceiro despesa = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);

        despesa.confirmarPagamento(hoje, contaBancariaId, FormaPagamento.PIX);

        assertThat(despesa.getStatus()).isEqualTo(StatusLancamento.PAGO);
        assertThat(despesa.getDataPagamento()).isEqualTo(hoje);
        assertThat(despesa.ehContaAPagar()).isFalse();
    }

    @Test
    void confirmarPagamentoEmReceitaLancaRegraDeNegocio() {
        LancamentoFinanceiro receita = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.RECEITA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);

        assertThatThrownBy(() -> receita.confirmarPagamento(hoje, contaBancariaId, FormaPagamento.PIX))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TIPO_INCOMPATIVEL_COM_CONFIRMACAO"));
    }

    @Test
    void confirmarRecebimentoEmReceitaPendenteMudaParaRecebido() {
        LancamentoFinanceiro receita = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.RECEITA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);

        receita.confirmarRecebimento(hoje, contaBancariaId, FormaPagamento.PIX);

        assertThat(receita.getStatus()).isEqualTo(StatusLancamento.RECEBIDO);
        assertThat(receita.getDataPagamento()).isEqualTo(hoje);
    }

    @Test
    void confirmarRecebimentoEmDespesaLancaRegraDeNegocio() {
        LancamentoFinanceiro despesa = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);

        assertThatThrownBy(() -> despesa.confirmarRecebimento(hoje, contaBancariaId, FormaPagamento.PIX))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TIPO_INCOMPATIVEL_COM_CONFIRMACAO"));
    }

    @Test
    void confirmarPagamentoDeUmLancamentoJaPagoLancaRegraDeNegocio() {
        LancamentoFinanceiro despesa = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);
        despesa.confirmarPagamento(hoje, contaBancariaId, FormaPagamento.PIX);

        assertThatThrownBy(() -> despesa.confirmarPagamento(hoje, contaBancariaId, FormaPagamento.PIX))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TRANSICAO_INVALIDA"));
    }

    @Test
    void cancelarUmLancamentoPendenteMudaParaCancelado() {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);

        lancamento.cancelar();

        assertThat(lancamento.getStatus()).isEqualTo(StatusLancamento.CANCELADO);
        assertThat(lancamento.ehContaAPagar()).isFalse();
    }

    @Test
    void cancelarUmLancamentoJaPagoLancaRegraDeNegocio() {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);
        lancamento.confirmarPagamento(hoje, contaBancariaId, FormaPagamento.PIX);

        assertThatThrownBy(lancamento::cancelar)
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TRANSICAO_INVALIDA"));
    }

    @Test
    void marcarComoAtrasadoMudaDePendenteParaAtrasado() {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje.minusDays(2), null, null, contaBancariaId, null, "Descrição de teste", null);

        lancamento.marcarComoAtrasado();

        assertThat(lancamento.getStatus()).isEqualTo(StatusLancamento.ATRASADO);
        assertThat(lancamento.ehContaAPagar()).isTrue();
    }

    @Test
    void marcarComoAtrasadoUmLancamentoJaPagoLancaRegraDeNegocio() {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);
        lancamento.confirmarPagamento(hoje, contaBancariaId, FormaPagamento.PIX);

        assertThatThrownBy(lancamento::marcarComoAtrasado)
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TRANSICAO_INVALIDA"));
    }

    @Test
    void novoDeRecorrenciaNasceComOrigemRecorrenciaEVinculoComARecorrencia() {
        UUID lancamentoRecorrenteId = UUID.randomUUID();

        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novoDeRecorrencia(ambienteFinanceiroId,
                lancamentoRecorrenteId, TipoLancamento.DESPESA, new BigDecimal("1500.00"), hoje, hoje, categoriaId,
                contaBancariaId, "Descrição de teste", "Gerado automaticamente pela recorrencia: Aluguel");

        assertThat(lancamento.getOrigem()).isEqualTo(OrigemLancamento.RECORRENCIA);
        assertThat(lancamento.getLancamentoRecorrenteId()).isEqualTo(lancamentoRecorrenteId);
        assertThat(lancamento.getStatus()).isEqualTo(StatusLancamento.PENDENTE);
        assertThat(lancamento.getValor()).isEqualByComparingTo("1500.00");
    }

    @Test
    void novoDeRecorrenciaComValorInvalidoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> LancamentoFinanceiro.novoDeRecorrencia(ambienteFinanceiroId, UUID.randomUUID(),
                TipoLancamento.DESPESA, BigDecimal.ZERO, hoje, hoje, null, contaBancariaId, "Descrição de teste", null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("VALOR_INVALIDO"));
    }

    @Test
    void reabrirUmaDespesaPagaComVencimentoFuturoVoltaParaPendenteELimpaADataDePagamento() {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje.plusDays(5), null, null, contaBancariaId, null, "Descrição de teste", null);
        lancamento.confirmarPagamento(hoje, contaBancariaId, FormaPagamento.PIX);

        lancamento.reabrir();

        assertThat(lancamento.getStatus()).isEqualTo(StatusLancamento.PENDENTE);
        assertThat(lancamento.getDataPagamento()).isNull();
        assertThat(lancamento.ehContaAPagar()).isTrue();
    }

    @Test
    void reabrirUmaDespesaPagaComVencimentoJaPassadoVoltaParaAtrasado() {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje.minusDays(3), null, null, contaBancariaId, null, "Descrição de teste", null);
        lancamento.confirmarPagamento(hoje, contaBancariaId, FormaPagamento.PIX);

        lancamento.reabrir();

        assertThat(lancamento.getStatus()).isEqualTo(StatusLancamento.ATRASADO);
    }

    @Test
    void reabrirUmaReceitaRecebidaVoltaParaPendente() {
        LancamentoFinanceiro receita = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.RECEITA,
                new BigDecimal("50.00"), hoje, hoje.plusDays(1), null, null, contaBancariaId, null, "Descrição de teste", null);
        receita.confirmarRecebimento(hoje, contaBancariaId, FormaPagamento.PIX);

        receita.reabrir();

        assertThat(receita.getStatus()).isEqualTo(StatusLancamento.PENDENTE);
        assertThat(receita.getDataPagamento()).isNull();
    }

    @Test
    void reabrirUmLancamentoAindaPendenteLancaRegraDeNegocio() {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, null, "Descrição de teste", null);

        assertThatThrownBy(lancamento::reabrir)
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TRANSICAO_INVALIDA"));
    }

    // `[Revisado - Etapa B]` anexarDocumento/getDocumentoId sairam de
    // LancamentoFinanceiro - o vinculo Documento <-> Lancamento (N:1) mora em
    // documento.lancamento_id; cobertura em DocumentoServiceTest.

    /** ADR-015: sem Conta Bancaria no Lancamento e sem Conta Bancaria na confirmacao, exige. */
    @Test
    void confirmarPagamentoSemContaBancariaNoLancamentoNemNaConfirmacaoLancaRegraDeNegocio() {
        LancamentoFinanceiro despesa = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, null, null, "Descrição de teste", null);

        assertThatThrownBy(() -> despesa.confirmarPagamento(hoje, null, FormaPagamento.PIX))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("CONTA_BANCARIA_OBRIGATORIA_PARA_QUITACAO"));
    }

    /** ADR-015: sem Forma de Pagamento no Lancamento e sem Forma de Pagamento na confirmacao, exige. */
    @Test
    void confirmarPagamentoSemFormaPagamentoNoLancamentoNemNaConfirmacaoLancaRegraDeNegocio() {
        LancamentoFinanceiro despesa = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, null, null, "Descrição de teste", null);

        assertThatThrownBy(() -> despesa.confirmarPagamento(hoje, contaBancariaId, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("FORMA_PAGAMENTO_OBRIGATORIA_PARA_QUITACAO"));
    }

    /** ADR-015: Lancamento sem Conta/Forma - a confirmacao fornece os dois e eles passam a ficar gravados. */
    @Test
    void confirmarPagamentoSemContaOuFormaNoLancamentoUsaEGravaOQueVeioDaConfirmacao() {
        LancamentoFinanceiro despesa = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, null, null, "Descrição de teste", null);

        despesa.confirmarPagamento(hoje, contaBancariaId, FormaPagamento.PIX);

        assertThat(despesa.getContaBancariaId()).isEqualTo(contaBancariaId);
        assertThat(despesa.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        assertThat(despesa.getStatus()).isEqualTo(StatusLancamento.PAGO);
    }

    /** ADR-015: Lancamento ja com Conta/Forma - a confirmacao ignora o que veio (nao pede de novo, nao sobrescreve). */
    @Test
    void confirmarPagamentoComContaEFormaJaPreenchidasIgnoraOQueVeioDaConfirmacao() {
        UUID outraContaBancariaId = UUID.randomUUID();
        LancamentoFinanceiro despesa = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.DESPESA,
                new BigDecimal("50.00"), hoje, hoje, null, null, contaBancariaId, FormaPagamento.BOLETO,
                "Descrição de teste", null);

        despesa.confirmarPagamento(hoje, outraContaBancariaId, FormaPagamento.PIX);

        assertThat(despesa.getContaBancariaId()).isEqualTo(contaBancariaId);
        assertThat(despesa.getFormaPagamento()).isEqualTo(FormaPagamento.BOLETO);
    }

    /** ADR-015: confirmarRecebimento segue a mesma resolucao de confirmarPagamento. */
    @Test
    void confirmarRecebimentoSemContaBancariaNoLancamentoNemNaConfirmacaoLancaRegraDeNegocio() {
        LancamentoFinanceiro receita = LancamentoFinanceiro.novo(ambienteFinanceiroId, TipoLancamento.RECEITA,
                new BigDecimal("50.00"), hoje, hoje, null, null, null, null, "Descrição de teste", null);

        assertThatThrownBy(() -> receita.confirmarRecebimento(hoje, null, FormaPagamento.PIX))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("CONTA_BANCARIA_OBRIGATORIA_PARA_QUITACAO"));
    }

    /** ADR-017, T2.1.3/T2.1.4: Lancamento de origem FATURA (juros rotativo ou parcela do parcelamento). */
    @Test
    void novoDeFaturaNasceComOrigemFaturaEVinculadoAoCartaoEAFatura() {
        UUID cartaoId = UUID.randomUUID();
        UUID faturaId = UUID.randomUUID();

        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novoDeFatura(ambienteFinanceiroId, cartaoId, faturaId,
                new BigDecimal("45.00"), hoje, hoje.plusDays(30), "Juros rotativo - Fatura 08/2026");

        assertThat(lancamento.getTipo()).isEqualTo(TipoLancamento.DESPESA);
        assertThat(lancamento.getStatus()).isEqualTo(StatusLancamento.PENDENTE);
        assertThat(lancamento.getOrigem()).isEqualTo(OrigemLancamento.FATURA);
        assertThat(lancamento.getCartaoId()).isEqualTo(cartaoId);
        assertThat(lancamento.getFaturaId()).isEqualTo(faturaId);
        assertThat(lancamento.getValor()).isEqualByComparingTo("45.00");
    }

    @Test
    void novoDeFaturaComValorInvalidoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> LancamentoFinanceiro.novoDeFatura(ambienteFinanceiroId, UUID.randomUUID(),
                UUID.randomUUID(), BigDecimal.ZERO, hoje, hoje, "Parcela 1/3"))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("VALOR_INVALIDO"));
    }
}
