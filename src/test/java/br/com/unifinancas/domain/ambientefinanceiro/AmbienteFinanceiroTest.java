package br.com.unifinancas.domain.ambientefinanceiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

class AmbienteFinanceiroTest {

    @Test
    void novoAmbienteNasceAtivoNoPlanoGratuitoComAntecedenciaPadrao() {
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Familia Silva", TipoAmbienteFinanceiro.FAMILIAR);

        assertThat(ambiente.getNome()).isEqualTo("Familia Silva");
        assertThat(ambiente.getTipo()).isEqualTo(TipoAmbienteFinanceiro.FAMILIAR);
        assertThat(ambiente.getPlano()).isEqualTo(PlanoAmbienteFinanceiro.GRATUITO);
        assertThat(ambiente.getStatus()).isEqualTo(StatusAmbienteFinanceiro.ATIVO);
        assertThat(ambiente.getDiasAntecedenciaNotificacaoVencimento()).isEqualTo(3);
        assertThat(ambiente.isOcrFallbackIaHabilitado()).isFalse();
        assertThat(ambiente.getCriadoEm()).isNotNull();
        assertThat(ambiente.getAtualizadoEm()).isNotNull();
    }

    /** ADR-016 (T3.1.2): opt-in por Ambiente Financeiro para o fallback OpenAI da cascata de extracao. */
    @Test
    void definirOcrFallbackIaHabilitadoLigaEDesligaAFlag() {
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Familia Silva", TipoAmbienteFinanceiro.FAMILIAR);

        ambiente.definirOcrFallbackIaHabilitado(true);
        assertThat(ambiente.isOcrFallbackIaHabilitado()).isTrue();

        ambiente.definirOcrFallbackIaHabilitado(false);
        assertThat(ambiente.isOcrFallbackIaHabilitado()).isFalse();
    }

    @Test
    void novoAmbienteComAntecedenciaCustomizadaUsaOValorInformado() {
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Familia Silva", TipoAmbienteFinanceiro.FAMILIAR, 7);

        assertThat(ambiente.getDiasAntecedenciaNotificacaoVencimento()).isEqualTo(7);
    }

    @Test
    void novoAmbienteComAntecedenciaZeroOuNegativaLancaRegraDeNegocio() {
        assertThatThrownBy(() -> AmbienteFinanceiro.novo("Familia Silva", TipoAmbienteFinanceiro.FAMILIAR, 0))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DIAS_ANTECEDENCIA_INVALIDO"));
        assertThatThrownBy(() -> AmbienteFinanceiro.novo("Familia Silva", TipoAmbienteFinanceiro.FAMILIAR, -1))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    /** RN-13, T1.11.4 (RNF-09/LGPD): quando o OWNER exclui a conta, o Ambiente inteiro e encerrado. */
    @Test
    void encerrarMudaOStatusParaEncerrado() {
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Familia Silva", TipoAmbienteFinanceiro.FAMILIAR);

        ambiente.encerrar();

        assertThat(ambiente.getStatus()).isEqualTo(StatusAmbienteFinanceiro.ENCERRADO);
    }

    /** Gap identificado pelo usuario: editar nome/dias de antecedencia, sem afetar tipo/plano/status. */
    @Test
    void editarAlteraNomeEDiasDeAntecedenciaSemAfetarTipoOuStatus() {
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Familia Silva", TipoAmbienteFinanceiro.FAMILIAR);

        ambiente.editar("Familia Silva Sobrenome", 10);

        assertThat(ambiente.getNome()).isEqualTo("Familia Silva Sobrenome");
        assertThat(ambiente.getDiasAntecedenciaNotificacaoVencimento()).isEqualTo(10);
        assertThat(ambiente.getTipo()).isEqualTo(TipoAmbienteFinanceiro.FAMILIAR);
        assertThat(ambiente.getStatus()).isEqualTo(StatusAmbienteFinanceiro.ATIVO);
    }

    @Test
    void editarComAntecedenciaNulaVoltaAoPadraoDeTresDias() {
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Familia Silva", TipoAmbienteFinanceiro.FAMILIAR, 10);

        ambiente.editar("Familia Silva", null);

        assertThat(ambiente.getDiasAntecedenciaNotificacaoVencimento()).isEqualTo(3);
    }

    @Test
    void editarComAntecedenciaZeroOuNegativaLancaRegraDeNegocio() {
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Familia Silva", TipoAmbienteFinanceiro.FAMILIAR);

        assertThatThrownBy(() -> ambiente.editar("Familia Silva", 0))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DIAS_ANTECEDENCIA_INVALIDO"));
    }
}
