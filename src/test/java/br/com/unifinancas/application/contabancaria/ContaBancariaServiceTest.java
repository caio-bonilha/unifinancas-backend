package br.com.unifinancas.application.contabancaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.contabancaria.ContaBancaria;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.contabancaria.TipoContaBancaria;
import br.com.unifinancas.domain.movimentacao.MovimentacaoRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContaBancariaServiceTest {

    private final ContaBancariaRepository contaBancariaRepository = mock(ContaBancariaRepository.class);
    private final MovimentacaoRepository movimentacaoRepository = mock(MovimentacaoRepository.class);
    private final ContaBancariaService service = new ContaBancariaService(contaBancariaRepository, movimentacaoRepository);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);

    @Test
    void listarRetornaAsContasDoAmbienteDoContexto() {
        ContaBancaria conta = ContaBancaria.novo(ambienteFinanceiroId, "Conta Corrente", TipoContaBancaria.CORRENTE,
                new BigDecimal("50.00"));
        when(contaBancariaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of(conta));

        List<ContaBancariaResumo> resultado = service.listar(contexto);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nome()).isEqualTo("Conta Corrente");
        assertThat(resultado.get(0).saldoAtual()).isEqualByComparingTo("50.00");
    }

    @Test
    void semNenhumaContaRetornaListaVazia() {
        when(contaBancariaRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of());

        assertThat(service.listar(contexto)).isEmpty();
    }

    @Test
    void criarPersisteAContaNoAmbienteDoContextoComOSaldoInformado() {
        ContaBancariaResumo resumo = service.criar(contexto, "Poupanca", TipoContaBancaria.POUPANCA, new BigDecimal("200.00"));

        ArgumentCaptor<ContaBancaria> capturada = ArgumentCaptor.forClass(ContaBancaria.class);
        verify(contaBancariaRepository).save(capturada.capture());
        assertThat(capturada.getValue().getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(capturada.getValue().getNome()).isEqualTo("Poupanca");
        assertThat(capturada.getValue().getTipo()).isEqualTo(TipoContaBancaria.POUPANCA);
        assertThat(capturada.getValue().getSaldoInicial()).isEqualByComparingTo("200.00");

        assertThat(resumo.nome()).isEqualTo("Poupanca");
        assertThat(resumo.saldoAtual()).isEqualByComparingTo("200.00");
        assertThat(resumo.ativa()).isTrue();
    }

    @Test
    void criarSemSaldoInicialUsaZero() {
        ContaBancariaResumo resumo = service.criar(contexto, "Carteira Digital", TipoContaBancaria.CARTEIRA_DIGITAL, null);

        assertThat(resumo.saldoInicial()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resumo.saldoAtual()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void editarAtualizaNomeETipo() {
        UUID contaId = UUID.randomUUID();
        ContaBancaria conta = ContaBancaria.novo(ambienteFinanceiroId, "Antigo", TipoContaBancaria.CORRENTE, BigDecimal.ZERO);
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaId, ambienteFinanceiroId)).thenReturn(Optional.of(conta));

        ContaBancariaResumo resumo = service.editar(contexto, contaId, "Novo Nome", TipoContaBancaria.POUPANCA);

        assertThat(resumo.nome()).isEqualTo("Novo Nome");
        assertThat(resumo.tipo()).isEqualTo(TipoContaBancaria.POUPANCA);
        verify(contaBancariaRepository).save(conta);
    }

    @Test
    void editarContaInexistenteOuDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID contaId = UUID.randomUUID();
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.editar(contexto, contaId, "X", TipoContaBancaria.CORRENTE))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void desativarEAtivarAlternamOFlag() {
        UUID contaId = UUID.randomUUID();
        ContaBancaria conta = ContaBancaria.novo(ambienteFinanceiroId, "Conta", TipoContaBancaria.CORRENTE, BigDecimal.ZERO);
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaId, ambienteFinanceiroId)).thenReturn(Optional.of(conta));

        ContaBancariaResumo desativada = service.desativar(contexto, contaId);
        assertThat(desativada.ativa()).isFalse();

        ContaBancariaResumo reativada = service.ativar(contexto, contaId);
        assertThat(reativada.ativa()).isTrue();
    }

    @Test
    void recalcularSaldoSomaSaldoInicialComAsMovimentacoesDaConta() {
        UUID contaId = UUID.randomUUID();
        ContaBancaria conta = ContaBancaria.novo(ambienteFinanceiroId, "Conta", TipoContaBancaria.CORRENTE, new BigDecimal("100.00"));
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaId, ambienteFinanceiroId)).thenReturn(Optional.of(conta));
        when(movimentacaoRepository.somarComSinalPorConta(ambienteFinanceiroId, contaId))
                .thenReturn(new BigDecimal("220.00"));

        service.recalcularSaldo(ambienteFinanceiroId, contaId);

        // 100 (inicial) + 220 (soma com sinal das Movimentacoes) = 320
        assertThat(conta.getSaldoAtual()).isEqualByComparingTo("320.00");
        verify(contaBancariaRepository).save(conta);
    }

    @Test
    void recalcularSaldoComSomaNegativaDasMovimentacoesSubtraiDoSaldoInicial() {
        UUID contaId = UUID.randomUUID();
        ContaBancaria conta = ContaBancaria.novo(ambienteFinanceiroId, "Conta", TipoContaBancaria.CORRENTE, new BigDecimal("500.00"));
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaId, ambienteFinanceiroId)).thenReturn(Optional.of(conta));
        when(movimentacaoRepository.somarComSinalPorConta(ambienteFinanceiroId, contaId))
                .thenReturn(new BigDecimal("70.00"));

        service.recalcularSaldo(ambienteFinanceiroId, contaId);

        // 500 (inicial) + 70 (soma com sinal das Movimentacoes) = 570
        assertThat(conta.getSaldoAtual()).isEqualByComparingTo("570.00");
    }

    @Test
    void recalcularSaldoDeContaInexistenteOuDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID contaId = UUID.randomUUID();
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.recalcularSaldo(ambienteFinanceiroId, contaId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
