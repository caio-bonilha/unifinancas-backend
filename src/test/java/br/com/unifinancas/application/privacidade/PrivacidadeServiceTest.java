package br.com.unifinancas.application.privacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.application.ambientefinanceiro.AmbienteFinanceiroService;
import br.com.unifinancas.application.usuario.EmissorTokenPort;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiroRepository;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.StatusAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.StatusVinculo;
import br.com.unifinancas.domain.ambientefinanceiro.TipoAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import br.com.unifinancas.domain.categoria.CategoriaRepository;
import br.com.unifinancas.domain.contabancaria.ContaBancaria;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.contabancaria.TipoContaBancaria;
import br.com.unifinancas.domain.contato.ContatoRepository;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrenteRepository;
import br.com.unifinancas.domain.movimentacao.MovimentacaoRepository;
import br.com.unifinancas.domain.orcamentometa.OrcamentoMetaRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.usuario.StatusUsuario;
import br.com.unifinancas.domain.usuario.Usuario;
import br.com.unifinancas.domain.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PrivacidadeServiceTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository =
            mock(UsuarioAmbienteFinanceiroRepository.class);
    private final AmbienteFinanceiroRepository ambienteFinanceiroRepository = mock(AmbienteFinanceiroRepository.class);
    // real, nao mock: PrivacidadeService.excluirConta reaproveita AmbienteFinanceiroService.encerrarAmbiente
    // (RN-13), e os mocks de repositorio acima ja bastam para exercitar o comportamento real dela.
    private final AmbienteFinanceiroService ambienteFinanceiroService =
            new AmbienteFinanceiroService(usuarioAmbienteFinanceiroRepository, ambienteFinanceiroRepository);
    private final ContaBancariaRepository contaBancariaRepository = mock(ContaBancariaRepository.class);
    private final CategoriaRepository categoriaRepository = mock(CategoriaRepository.class);
    private final ContatoRepository contatoRepository = mock(ContatoRepository.class);
    private final LancamentoRepository lancamentoRepository = mock(LancamentoRepository.class);
    private final LancamentoRecorrenteRepository lancamentoRecorrenteRepository = mock(LancamentoRecorrenteRepository.class);
    private final MovimentacaoRepository movimentacaoRepository = mock(MovimentacaoRepository.class);
    private final OrcamentoMetaRepository orcamentoMetaRepository = mock(OrcamentoMetaRepository.class);
    private final EmissorTokenPort emissorTokenPort = mock(EmissorTokenPort.class);

    private final PrivacidadeService service = new PrivacidadeService(usuarioRepository,
            usuarioAmbienteFinanceiroRepository, ambienteFinanceiroRepository, ambienteFinanceiroService,
            contaBancariaRepository, categoriaRepository, contatoRepository, lancamentoRepository,
            lancamentoRecorrenteRepository, movimentacaoRepository, orcamentoMetaRepository, emissorTokenPort);

    private final UUID usuarioId = UUID.randomUUID();

    private Usuario usuarioAtivo() {
        Usuario usuario = Usuario.novo("Maria Silva", "maria@example.com", "hash");
        ReflectionTestUtils.setField(usuario, "id", usuarioId);
        return usuario;
    }

    @Test
    void exportarDadosDeUsuarioInexistenteLancaRecursoNaoEncontrado() {
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exportarDados(usuarioId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void exportarDadosSoIncluiAmbientesOndeEOwner() {
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuarioAtivo()));

        UUID ambienteOwnerId = UUID.randomUUID();
        UUID ambienteMembroId = UUID.randomUUID();
        UsuarioAmbienteFinanceiro vinculoOwner =
                UsuarioAmbienteFinanceiro.vincularComPerfil(usuarioId, ambienteOwnerId, PerfilUsuarioAmbiente.OWNER);
        UsuarioAmbienteFinanceiro vinculoMembro =
                UsuarioAmbienteFinanceiro.vincularComPerfil(usuarioId, ambienteMembroId, PerfilUsuarioAmbiente.MEMBRO);
        when(usuarioAmbienteFinanceiroRepository.findAllByUsuarioIdAndStatus(usuarioId, StatusVinculo.ATIVO))
                .thenReturn(List.of(vinculoOwner, vinculoMembro));

        AmbienteFinanceiro ambienteOwner = AmbienteFinanceiro.novo("Minha Familia", TipoAmbienteFinanceiro.FAMILIAR);
        ReflectionTestUtils.setField(ambienteOwner, "id", ambienteOwnerId);
        when(ambienteFinanceiroRepository.findById(ambienteOwnerId)).thenReturn(Optional.of(ambienteOwner));

        ContaBancaria conta = ContaBancaria.novo(ambienteOwnerId, "Conta Corrente", TipoContaBancaria.CORRENTE, BigDecimal.TEN);
        when(contaBancariaRepository.findAllByAmbienteFinanceiroId(ambienteOwnerId)).thenReturn(List.of(conta));
        when(categoriaRepository.findAllByAmbienteFinanceiroId(ambienteOwnerId)).thenReturn(List.of());
        when(contatoRepository.findAllByAmbienteFinanceiroId(ambienteOwnerId)).thenReturn(List.of());
        when(lancamentoRepository.findAllByAmbienteFinanceiroId(ambienteOwnerId)).thenReturn(List.of());
        when(lancamentoRecorrenteRepository.findAllByAmbienteFinanceiroId(ambienteOwnerId)).thenReturn(List.of());
        when(movimentacaoRepository.findAllByAmbienteFinanceiroId(ambienteOwnerId)).thenReturn(List.of());
        when(orcamentoMetaRepository.findAllByAmbienteFinanceiroId(ambienteOwnerId)).thenReturn(List.of());

        ExportacaoDadosResumo exportacao = service.exportarDados(usuarioId);

        assertThat(exportacao.usuario().id()).isEqualTo(usuarioId);
        assertThat(exportacao.ambientes()).hasSize(1);
        assertThat(exportacao.ambientes().get(0).id()).isEqualTo(ambienteOwnerId);
        assertThat(exportacao.ambientes().get(0).contasBancarias()).hasSize(1);
        assertThat(exportacao.ambientes().get(0).contasBancarias().get(0).nome()).isEqualTo("Conta Corrente");
    }

    @Test
    void excluirContaDeUsuarioJaExcluidoLancaRegraDeNegocio() {
        Usuario usuarioExcluido = usuarioAtivo();
        usuarioExcluido.excluir();
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuarioExcluido));

        assertThatThrownBy(() -> service.excluirConta(usuarioId))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("USUARIO_JA_EXCLUIDO"));
    }

    @Test
    void excluirContaAnonimizaOUsuarioERevogaOsTokens() {
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuarioAtivo()));
        when(usuarioAmbienteFinanceiroRepository.findAllByUsuarioIdAndStatus(usuarioId, StatusVinculo.ATIVO))
                .thenReturn(List.of());

        service.excluirConta(usuarioId);

        var captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusUsuario.EXCLUIDO);
        assertThat(captor.getValue().getEmail()).isNotEqualTo("maria@example.com");
        verify(emissorTokenPort).revogarTodosDoUsuario(usuarioId);
    }

    @Test
    void excluirContaQuandoEOwnerEncerraOAmbienteERemoveTodosOsMembros() {
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuarioAtivo()));

        UUID ambienteId = UUID.randomUUID();
        UsuarioAmbienteFinanceiro vinculoOwner =
                UsuarioAmbienteFinanceiro.vincularComPerfil(usuarioId, ambienteId, PerfilUsuarioAmbiente.OWNER);
        when(usuarioAmbienteFinanceiroRepository.findAllByUsuarioIdAndStatus(usuarioId, StatusVinculo.ATIVO))
                .thenReturn(List.of(vinculoOwner));

        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Familia Silva", TipoAmbienteFinanceiro.FAMILIAR);
        ReflectionTestUtils.setField(ambiente, "id", ambienteId);
        when(ambienteFinanceiroRepository.findById(ambienteId)).thenReturn(Optional.of(ambiente));

        UsuarioAmbienteFinanceiro outroMembro =
                UsuarioAmbienteFinanceiro.vincularComPerfil(UUID.randomUUID(), ambienteId, PerfilUsuarioAmbiente.MEMBRO);
        when(usuarioAmbienteFinanceiroRepository.findAllByAmbienteFinanceiroIdAndStatus(ambienteId, StatusVinculo.ATIVO))
                .thenReturn(List.of(vinculoOwner, outroMembro));

        service.excluirConta(usuarioId);

        assertThat(ambiente.getStatus()).isEqualTo(StatusAmbienteFinanceiro.ENCERRADO);
        assertThat(vinculoOwner.getStatus()).isEqualTo(StatusVinculo.REMOVIDO);
        assertThat(outroMembro.getStatus()).isEqualTo(StatusVinculo.REMOVIDO);
        verify(ambienteFinanceiroRepository).save(ambiente);
    }

    @Test
    void excluirContaQuandoESoMembroRemoveSoOProprioVinculoENaoEncerraOAmbiente() {
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuarioAtivo()));

        UUID ambienteId = UUID.randomUUID();
        UsuarioAmbienteFinanceiro vinculoMembro =
                UsuarioAmbienteFinanceiro.vincularComPerfil(usuarioId, ambienteId, PerfilUsuarioAmbiente.MEMBRO);
        when(usuarioAmbienteFinanceiroRepository.findAllByUsuarioIdAndStatus(usuarioId, StatusVinculo.ATIVO))
                .thenReturn(List.of(vinculoMembro));

        service.excluirConta(usuarioId);

        assertThat(vinculoMembro.getStatus()).isEqualTo(StatusVinculo.REMOVIDO);
        verify(ambienteFinanceiroRepository, never()).findById(any());
        verify(ambienteFinanceiroRepository, never()).save(any());
    }
}
