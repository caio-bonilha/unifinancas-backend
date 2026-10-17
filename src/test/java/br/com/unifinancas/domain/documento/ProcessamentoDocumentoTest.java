package br.com.unifinancas.domain.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessamentoDocumentoTest {

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();

    private ProcessamentoDocumento novo() {
        return ProcessamentoDocumento.novo(ambienteFinanceiroId, documentoId);
    }

    @Test
    void novoProcessamentoNascePendenteSemTentativasNemDados() {
        ProcessamentoDocumento p = novo();

        assertThat(p.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(p.getDocumentoId()).isEqualTo(documentoId);
        assertThat(p.getStatus()).isEqualTo(StatusProcessamento.PENDENTE);
        assertThat(p.getTentativas()).isZero();
        assertThat(p.getDadosExtraidos()).isNull();
        assertThat(p.getErroMensagem()).isNull();
        assertThat(p.getLancamentoGeradoId()).isNull();
        assertThat(p.getCompraGeradaId()).isNull();
        assertThat(p.getIniciadoEm()).isNull();
        assertThat(p.getConcluidoEm()).isNull();
    }

    @Test
    void novoSemAmbienteOuDocumentoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> ProcessamentoDocumento.novo(null, documentoId))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("AMBIENTE_FINANCEIRO_OBRIGATORIO"));
        assertThatThrownBy(() -> ProcessamentoDocumento.novo(ambienteFinanceiroId, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DOCUMENTO_OBRIGATORIO"));
    }

    @Test
    void iniciarProcessamentoContaATentativaEMarcaOInicio() {
        ProcessamentoDocumento p = novo();

        p.iniciarProcessamento();

        assertThat(p.getStatus()).isEqualTo(StatusProcessamento.PROCESSANDO);
        assertThat(p.getTentativas()).isEqualTo(1);
        assertThat(p.getIniciadoEm()).isNotNull();
        assertThat(p.getConcluidoEm()).isNull();
    }

    @Test
    void registrarExtracaoMoveParaAguardandoRevisaoComOsDados() {
        ProcessamentoDocumento p = novo();
        p.iniciarProcessamento();

        p.registrarExtracao("{\"valor\":123.45}");

        assertThat(p.getStatus()).isEqualTo(StatusProcessamento.AGUARDANDO_REVISAO);
        assertThat(p.getDadosExtraidos()).isEqualTo("{\"valor\":123.45}");
        assertThat(p.getConcluidoEm()).isNotNull();
    }

    @Test
    void registrarExtracaoSemDadosLancaRegraDeNegocio() {
        ProcessamentoDocumento p = novo();
        p.iniciarProcessamento();

        assertThatThrownBy(() -> p.registrarExtracao("  "))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("DADOS_EXTRAIDOS_OBRIGATORIOS"));
    }

    @Test
    void registrarErroMoveParaErroComAMensagem() {
        ProcessamentoDocumento p = novo();
        p.iniciarProcessamento();

        p.registrarErro("provedor indisponivel");

        assertThat(p.getStatus()).isEqualTo(StatusProcessamento.ERRO);
        assertThat(p.getErroMensagem()).isEqualTo("provedor indisponivel");
        assertThat(p.getConcluidoEm()).isNotNull();
    }

    @Test
    void retryAPartirDeErroContaNovaTentativaELimpaOResultadoAnterior() {
        ProcessamentoDocumento p = novo();
        p.iniciarProcessamento();
        p.registrarErro("timeout");

        p.iniciarProcessamento();

        assertThat(p.getStatus()).isEqualTo(StatusProcessamento.PROCESSANDO);
        assertThat(p.getTentativas()).isEqualTo(2);
        assertThat(p.getErroMensagem()).isNull();
        assertThat(p.getConcluidoEm()).isNull();
    }

    @Test
    void confirmarComLancamentoMoveParaConfirmado() {
        ProcessamentoDocumento p = novo();
        p.iniciarProcessamento();
        p.registrarExtracao("{}");
        UUID lancamentoId = UUID.randomUUID();

        p.confirmar(lancamentoId, null);

        assertThat(p.getStatus()).isEqualTo(StatusProcessamento.CONFIRMADO);
        assertThat(p.getLancamentoGeradoId()).isEqualTo(lancamentoId);
        assertThat(p.getCompraGeradaId()).isNull();
    }

    @Test
    void confirmarComCompraMoveParaConfirmado() {
        ProcessamentoDocumento p = novo();
        p.iniciarProcessamento();
        p.registrarExtracao("{}");
        UUID compraId = UUID.randomUUID();

        p.confirmar(null, compraId);

        assertThat(p.getStatus()).isEqualTo(StatusProcessamento.CONFIRMADO);
        assertThat(p.getCompraGeradaId()).isEqualTo(compraId);
        assertThat(p.getLancamentoGeradoId()).isNull();
    }

    @Test
    void confirmarComOsDoisOuNenhumResultadoLancaRegraDeNegocio() {
        ProcessamentoDocumento comDois = novo();
        comDois.iniciarProcessamento();
        comDois.registrarExtracao("{}");
        assertThatThrownBy(() -> comDois.confirmar(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("RESULTADO_CONFIRMACAO_INVALIDO"));

        ProcessamentoDocumento semNenhum = novo();
        semNenhum.iniciarProcessamento();
        semNenhum.registrarExtracao("{}");
        assertThatThrownBy(() -> semNenhum.confirmar(null, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("RESULTADO_CONFIRMACAO_INVALIDO"));
    }

    @Test
    void transicoesForaDeOrdemLancamTransicaoInvalida() {
        ProcessamentoDocumento pendente = novo();
        assertThatThrownBy(() -> pendente.registrarExtracao("{}"))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TRANSICAO_INVALIDA"));
        assertThatThrownBy(() -> pendente.registrarErro("x"))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TRANSICAO_INVALIDA"));
        assertThatThrownBy(() -> pendente.confirmar(UUID.randomUUID(), null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TRANSICAO_INVALIDA"));

        ProcessamentoDocumento processando = novo();
        processando.iniciarProcessamento();
        assertThatThrownBy(processando::iniciarProcessamento)
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TRANSICAO_INVALIDA"));
    }
}
