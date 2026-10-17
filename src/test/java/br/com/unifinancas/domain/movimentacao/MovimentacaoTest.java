package br.com.unifinancas.domain.movimentacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MovimentacaoTest {

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID contaBancariaId = UUID.randomUUID();
    private final UUID lancamentoId = UUID.randomUUID();
    private final LocalDate hoje = LocalDate.now();

    @Test
    void movimentacaoParaLancamentoNasceVinculadaEComGrupoTransferenciaNulo() {
        Movimentacao movimentacao = Movimentacao.paraLancamento(ambienteFinanceiroId, contaBancariaId,
                TipoMovimentacao.DEBITO, new BigDecimal("150.00"), hoje, "Conta de luz", lancamentoId);

        assertThat(movimentacao.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(movimentacao.getContaBancariaId()).isEqualTo(contaBancariaId);
        assertThat(movimentacao.getTipo()).isEqualTo(TipoMovimentacao.DEBITO);
        assertThat(movimentacao.getValor()).isEqualByComparingTo("150.00");
        assertThat(movimentacao.getData()).isEqualTo(hoje);
        assertThat(movimentacao.getDescricao()).isEqualTo("Conta de luz");
        assertThat(movimentacao.getLancamentoId()).isEqualTo(lancamentoId);
        assertThat(movimentacao.getGrupoTransferenciaId()).isNull();
        assertThat(movimentacao.getOrigem()).isEqualTo(OrigemMovimentacao.LIQUIDACAO);
        assertThat(movimentacao.getCategoriaId()).isNull();
        assertThat(movimentacao.getCriadoEm()).isNotNull();
    }

    @Test
    void movimentacaoParaTransferenciaNasceComGrupoECpmLancamentoNulo() {
        UUID grupoTransferenciaId = UUID.randomUUID();

        Movimentacao movimentacao = Movimentacao.paraTransferencia(ambienteFinanceiroId, contaBancariaId,
                TipoMovimentacao.CREDITO, new BigDecimal("50.00"), hoje, "Transferência recebida", grupoTransferenciaId);

        assertThat(movimentacao.getGrupoTransferenciaId()).isEqualTo(grupoTransferenciaId);
        assertThat(movimentacao.getLancamentoId()).isNull();
        assertThat(movimentacao.getOrigem()).isEqualTo(OrigemMovimentacao.TRANSFERENCIA);
    }

    /** ADR-014: Movimentacao avulsa - sem Lancamento nem Transferencia por tras, categoriaId opcional. */
    @Test
    void movimentacaoParaAvulsaNasceComOrigemManualESemVinculos() {
        UUID categoriaId = UUID.randomUUID();

        Movimentacao movimentacao = Movimentacao.paraAvulsa(ambienteFinanceiroId, contaBancariaId,
                TipoMovimentacao.DEBITO, new BigDecimal("12.90"), hoje, "Tarifa de manutenção", categoriaId);

        assertThat(movimentacao.getOrigem()).isEqualTo(OrigemMovimentacao.MANUAL);
        assertThat(movimentacao.getCategoriaId()).isEqualTo(categoriaId);
        assertThat(movimentacao.getLancamentoId()).isNull();
        assertThat(movimentacao.getGrupoTransferenciaId()).isNull();
    }

    @Test
    void movimentacaoParaAvulsaAceitaCategoriaNula() {
        Movimentacao movimentacao = Movimentacao.paraAvulsa(ambienteFinanceiroId, contaBancariaId,
                TipoMovimentacao.CREDITO, new BigDecimal("5.00"), hoje, "Juros creditados", null);

        assertThat(movimentacao.getCategoriaId()).isNull();
    }

    /** ADR-014: so a avulsa (origem=MANUAL) pode ser editada. */
    @Test
    void editarAlteraOsCamposDeUmaMovimentacaoAvulsa() {
        UUID categoriaId = UUID.randomUUID();
        Movimentacao movimentacao = Movimentacao.paraAvulsa(ambienteFinanceiroId, contaBancariaId,
                TipoMovimentacao.DEBITO, new BigDecimal("12.90"), hoje, "Tarifa", null);

        movimentacao.editar(TipoMovimentacao.CREDITO, new BigDecimal("20.00"), hoje.minusDays(1), "Tarifa corrigida", categoriaId);

        assertThat(movimentacao.getTipo()).isEqualTo(TipoMovimentacao.CREDITO);
        assertThat(movimentacao.getValor()).isEqualByComparingTo("20.00");
        assertThat(movimentacao.getData()).isEqualTo(hoje.minusDays(1));
        assertThat(movimentacao.getDescricao()).isEqualTo("Tarifa corrigida");
        assertThat(movimentacao.getCategoriaId()).isEqualTo(categoriaId);
    }

    @Test
    void editarUmaMovimentacaoDeLiquidacaoLancaRegraDeNegocio() {
        Movimentacao movimentacao = Movimentacao.paraLancamento(ambienteFinanceiroId, contaBancariaId,
                TipoMovimentacao.DEBITO, new BigDecimal("100.00"), hoje, "Aluguel", lancamentoId);

        assertThatThrownBy(() -> movimentacao.editar(TipoMovimentacao.DEBITO, new BigDecimal("110.00"), hoje, "Aluguel", null))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", "MOVIMENTACAO_NAO_EDITAVEL");
    }

    @Test
    void editarUmaMovimentacaoDeTransferenciaLancaRegraDeNegocio() {
        Movimentacao movimentacao = Movimentacao.paraTransferencia(ambienteFinanceiroId, contaBancariaId,
                TipoMovimentacao.CREDITO, new BigDecimal("50.00"), hoje, "Transferência recebida", UUID.randomUUID());

        assertThatThrownBy(() -> movimentacao.editar(TipoMovimentacao.CREDITO, new BigDecimal("60.00"), hoje, "Transferência", null))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", "MOVIMENTACAO_NAO_EDITAVEL");
    }

    @Test
    void valorComSinalECreditoPositivoEDebitoNegativo() {
        Movimentacao credito = Movimentacao.paraLancamento(ambienteFinanceiroId, contaBancariaId,
                TipoMovimentacao.CREDITO, new BigDecimal("100.00"), hoje, "Salário", lancamentoId);
        Movimentacao debito = Movimentacao.paraLancamento(ambienteFinanceiroId, contaBancariaId,
                TipoMovimentacao.DEBITO, new BigDecimal("100.00"), hoje, "Aluguel", lancamentoId);

        assertThat(credito.valorComSinal()).isEqualByComparingTo("100.00");
        assertThat(debito.valorComSinal()).isEqualByComparingTo("-100.00");
    }

    @Test
    void valorZeroOuNegativoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Movimentacao.paraLancamento(ambienteFinanceiroId, contaBancariaId,
                TipoMovimentacao.DEBITO, BigDecimal.ZERO, hoje, "Descrição", lancamentoId))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", "VALOR_INVALIDO");
    }

    @Test
    void descricaoEmBrancoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Movimentacao.paraLancamento(ambienteFinanceiroId, contaBancariaId,
                TipoMovimentacao.DEBITO, new BigDecimal("10.00"), hoje, "  ", lancamentoId))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", "DESCRICAO_OBRIGATORIA");
    }
}
