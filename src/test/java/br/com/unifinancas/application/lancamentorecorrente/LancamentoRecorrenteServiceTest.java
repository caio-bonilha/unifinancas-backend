package br.com.unifinancas.application.lancamentorecorrente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.categoria.Categoria;
import br.com.unifinancas.domain.categoria.CategoriaRepository;
import br.com.unifinancas.domain.categoria.TipoCategoria;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.lancamentorecorrente.FrequenciaRecorrencia;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrente;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrenteRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class LancamentoRecorrenteServiceTest {

    private final LancamentoRecorrenteRepository lancamentoRecorrenteRepository = mock(LancamentoRecorrenteRepository.class);
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final ContaBancariaRepository contaBancariaRepository = mock(ContaBancariaRepository.class);
    private final LancamentoRecorrenteService service =
            new LancamentoRecorrenteService(lancamentoRecorrenteRepository, categoriaRepository, contaBancariaRepository);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);
    private final UUID contaBancariaId = UUID.randomUUID();
    private final LocalDate hoje = LocalDate.now();

    @Test
    void criarPersisteARecorrenciaNoAmbienteDoContexto() {
        LancamentoRecorrenteResumo resumo = service.criar(contexto, "Aluguel", TipoLancamento.DESPESA,
                new BigDecimal("1500.00"), null, null, FrequenciaRecorrencia.MENSAL, 5, hoje, null);

        ArgumentCaptor<LancamentoRecorrente> capturado = ArgumentCaptor.forClass(LancamentoRecorrente.class);
        verify(lancamentoRecorrenteRepository).save(capturado.capture());
        assertThat(capturado.getValue().getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(resumo.descricao()).isEqualTo("Aluguel");
        assertThat(resumo.ativo()).isTrue();
    }

    @Test
    void criarComCategoriaDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID categoriaId = UUID.randomUUID();
        when(categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(contexto, "Aluguel", TipoLancamento.DESPESA, new BigDecimal("100.00"),
                categoriaId, null, FrequenciaRecorrencia.MENSAL, 5, hoje, null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CATEGORIA_NAO_ENCONTRADA"));
    }

    @Test
    void criarComContaBancariaDeOutroAmbienteLancaRecursoNaoEncontrado() {
        when(contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(contexto, "Aluguel", TipoLancamento.DESPESA, new BigDecimal("100.00"),
                null, contaBancariaId, FrequenciaRecorrencia.MENSAL, 5, hoje, null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("CONTA_BANCARIA_NAO_ENCONTRADA"));
    }

    @Test
    void listarRetornaAsRecorrenciasDoAmbiente() {
        Categoria categoria = Categoria.novo(ambienteFinanceiroId, "Moradia", TipoCategoria.DESPESA, null);
        LancamentoRecorrente recorrente = LancamentoRecorrente.novo(ambienteFinanceiroId, "Aluguel", TipoLancamento.DESPESA,
                new BigDecimal("1500.00"), categoria.getId(), null, FrequenciaRecorrencia.MENSAL, 5, hoje, null);
        when(lancamentoRecorrenteRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of(recorrente));

        List<LancamentoRecorrenteResumo> resultado = service.listar(contexto);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).descricao()).isEqualTo("Aluguel");
    }

    @Test
    void editarAtualizaOsCamposDaRecorrenciaEncontrada() {
        LancamentoRecorrente recorrente = LancamentoRecorrente.novo(ambienteFinanceiroId, "Aluguel", TipoLancamento.DESPESA,
                new BigDecimal("1500.00"), null, null, FrequenciaRecorrencia.MENSAL, 5, hoje, null);
        UUID recorrenteId = UUID.randomUUID();
        ReflectionTestUtils.setField(recorrente, "id", recorrenteId);
        when(lancamentoRecorrenteRepository.findByIdAndAmbienteFinanceiroId(recorrenteId, ambienteFinanceiroId))
                .thenReturn(Optional.of(recorrente));

        LancamentoRecorrenteResumo resumo = service.editar(contexto, recorrenteId, "Aluguel novo",
                new BigDecimal("1600.00"), null, null, FrequenciaRecorrencia.MENSAL, 10, hoje, null);

        assertThat(resumo.descricao()).isEqualTo("Aluguel novo");
        assertThat(resumo.valor()).isEqualByComparingTo("1600.00");
        verify(lancamentoRecorrenteRepository).save(recorrente);
    }

    @Test
    void editarRecorrenciaInexistenteLancaRecursoNaoEncontrado() {
        UUID recorrenteId = UUID.randomUUID();
        when(lancamentoRecorrenteRepository.findByIdAndAmbienteFinanceiroId(recorrenteId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editar(contexto, recorrenteId, "Aluguel", new BigDecimal("100.00"), null, null,
                FrequenciaRecorrencia.MENSAL, 5, hoje, null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNaoEncontradoException) e).getCodigo()).isEqualTo("LANCAMENTO_RECORRENTE_NAO_ENCONTRADO"));
    }

    @Test
    void desativarEReativarMudamOStatusDaRecorrencia() {
        LancamentoRecorrente recorrente = LancamentoRecorrente.novo(ambienteFinanceiroId, "Aluguel", TipoLancamento.DESPESA,
                new BigDecimal("1500.00"), null, null, FrequenciaRecorrencia.MENSAL, 5, hoje, null);
        UUID recorrenteId = UUID.randomUUID();
        ReflectionTestUtils.setField(recorrente, "id", recorrenteId);
        when(lancamentoRecorrenteRepository.findByIdAndAmbienteFinanceiroId(recorrenteId, ambienteFinanceiroId))
                .thenReturn(Optional.of(recorrente));

        LancamentoRecorrenteResumo desativado = service.desativar(contexto, recorrenteId);
        assertThat(desativado.ativo()).isFalse();

        LancamentoRecorrenteResumo reativado = service.reativar(contexto, recorrenteId);
        assertThat(reativado.ativo()).isTrue();
    }
}
