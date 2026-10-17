package br.com.unifinancas.application.cartao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.cartao.Cartao;
import br.com.unifinancas.domain.cartao.CartaoRepository;
import br.com.unifinancas.domain.contabancaria.ContaBancaria;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.contabancaria.TipoContaBancaria;
import br.com.unifinancas.domain.fatura.Fatura;
import br.com.unifinancas.domain.fatura.FaturaRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CartaoServiceTest {

    private final CartaoRepository cartaoRepository = mock(CartaoRepository.class);
    private final ContaBancariaRepository contaBancariaRepository = mock(ContaBancariaRepository.class);
    private final FaturaRepository faturaRepository = mock(FaturaRepository.class);
    private final CartaoService service = new CartaoService(cartaoRepository, contaBancariaRepository, faturaRepository);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);

    @Test
    void criarPersisteOCartaoEAPrimeiraFaturaAberta() {
        CartaoResumo resumo = service.criar(contexto, null, "Nubank", new BigDecimal("5000.00"), 10, 17, new BigDecimal("12.00"));

        ArgumentCaptor<Cartao> cartaoCapturado = ArgumentCaptor.forClass(Cartao.class);
        verify(cartaoRepository).save(cartaoCapturado.capture());
        assertThat(cartaoCapturado.getValue().getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(cartaoCapturado.getValue().getNome()).isEqualTo("Nubank");

        ArgumentCaptor<Fatura> faturaCapturada = ArgumentCaptor.forClass(Fatura.class);
        verify(faturaRepository).save(faturaCapturada.capture());
        assertThat(faturaCapturada.getValue().getCartaoId()).isEqualTo(cartaoCapturado.getValue().getId());

        assertThat(resumo.nome()).isEqualTo("Nubank");
        assertThat(resumo.limite()).isEqualByComparingTo("5000.00");
        assertThat(resumo.ativo()).isTrue();
    }

    @Test
    void criarComContaBancariaDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID contaBancariaId = UUID.randomUUID();
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(contexto, contaBancariaId, "Nubank", BigDecimal.TEN, 10, 17, null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CONTA_BANCARIA_NAO_ENCONTRADA"));
    }

    @Test
    void criarComContaBancariaValidaFunciona() {
        UUID contaBancariaId = UUID.randomUUID();
        ContaBancaria conta = ContaBancaria.novo(ambienteFinanceiroId, "Conta", TipoContaBancaria.CORRENTE, BigDecimal.ZERO);
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteFinanceiroId))
                .thenReturn(Optional.of(conta));

        CartaoResumo resumo = service.criar(contexto, contaBancariaId, "Nubank", BigDecimal.TEN, 10, 17, null);

        assertThat(resumo.contaBancariaId()).isEqualTo(contaBancariaId);
    }

    @Test
    void editarUmCartaoInexistenteLancaRecursoNaoEncontrado() {
        UUID cartaoId = UUID.randomUUID();
        when(cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editar(contexto, cartaoId, null, "Novo nome", BigDecimal.TEN, 10, 17, null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CARTAO_NAO_ENCONTRADO"));
    }

    @Test
    void editarUmCartaoExistenteAtualizaOsCampos() {
        UUID cartaoId = UUID.randomUUID();
        Cartao cartao = Cartao.novo(ambienteFinanceiroId, null, "Nubank", BigDecimal.TEN, 10, 17, null);
        ReflectionTestUtils.setField(cartao, "id", cartaoId);
        when(cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, ambienteFinanceiroId)).thenReturn(Optional.of(cartao));

        CartaoResumo resumo = service.editar(contexto, cartaoId, null, "Nubank Ultravioleta", new BigDecimal("8000.00"),
                5, 12, new BigDecimal("15.00"));

        assertThat(resumo.nome()).isEqualTo("Nubank Ultravioleta");
        assertThat(resumo.limite()).isEqualByComparingTo("8000.00");
        assertThat(resumo.taxaJurosRotativoMensal()).isEqualByComparingTo("15.00");
    }

    @Test
    void ativarEDesativarAlternamOStatus() {
        UUID cartaoId = UUID.randomUUID();
        Cartao cartao = Cartao.novo(ambienteFinanceiroId, null, "Nubank", BigDecimal.TEN, 10, 17, null);
        ReflectionTestUtils.setField(cartao, "id", cartaoId);
        when(cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, ambienteFinanceiroId)).thenReturn(Optional.of(cartao));

        CartaoResumo desativado = service.desativar(contexto, cartaoId);
        assertThat(desativado.ativo()).isFalse();

        CartaoResumo ativado = service.ativar(contexto, cartaoId);
        assertThat(ativado.ativo()).isTrue();
    }

    @Test
    void listarRetornaOsCartoesDoAmbiente() {
        Cartao cartao = Cartao.novo(ambienteFinanceiroId, null, "Nubank", BigDecimal.TEN, 10, 17, null);
        when(cartaoRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(java.util.List.of(cartao));

        assertThat(service.listar(contexto)).hasSize(1);
    }
}
