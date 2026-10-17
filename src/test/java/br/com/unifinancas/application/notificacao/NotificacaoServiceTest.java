package br.com.unifinancas.application.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.notificacao.CanalNotificacao;
import br.com.unifinancas.domain.notificacao.Notificacao;
import br.com.unifinancas.domain.notificacao.NotificacaoRepository;
import br.com.unifinancas.domain.notificacao.TipoNotificacao;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class NotificacaoServiceTest {

    private final NotificacaoRepository notificacaoRepository = mock(NotificacaoRepository.class);
    private final EnfileiradorDeEnvioPort enfileiradorDeEnvioPort = mock(EnfileiradorDeEnvioPort.class);
    private final NotificacaoService service = new NotificacaoService(notificacaoRepository, enfileiradorDeEnvioPort);

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);

    @Test
    void criarSemCanaisAdicionaisPersisteSoUmaLinhaInAppENaoEnfileiraEnvio() {
        when(notificacaoRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(this::devolverOMesmoArgumento);

        List<Notificacao> criadas = service.criar(ambienteFinanceiroId, usuarioId, TipoNotificacao.VENCIMENTO_CONTA,
                "Titulo", "Mensagem", "lancamento", UUID.randomUUID(), null);

        assertThat(criadas).hasSize(1);
        assertThat(criadas.get(0).getCanal()).isEqualTo(CanalNotificacao.IN_APP);
        verify(notificacaoRepository).save(org.mockito.ArgumentMatchers.any());
        verify(enfileiradorDeEnvioPort, never()).enfileirar(any());
    }

    @Test
    void criarComCanalAdicionalPersisteDuasLinhasEEnfileiraOEnvioSoDoCanalAdicional() {
        when(notificacaoRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(this::devolverOMesmoArgumento);

        List<Notificacao> criadas = service.criar(ambienteFinanceiroId, usuarioId, TipoNotificacao.ORCAMENTO_LIMITE,
                "Titulo", "Mensagem", "orcamento_meta", UUID.randomUUID(), Set.of(CanalNotificacao.EMAIL));

        assertThat(criadas).hasSize(2);
        assertThat(criadas).extracting(Notificacao::getCanal).containsExactlyInAnyOrder(CanalNotificacao.IN_APP, CanalNotificacao.EMAIL);
        Notificacao linhaEmail = criadas.stream().filter(n -> n.getCanal() == CanalNotificacao.EMAIL).findFirst().orElseThrow();
        verify(enfileiradorDeEnvioPort).enfileirar(linhaEmail.getId());
        verify(enfileiradorDeEnvioPort, org.mockito.Mockito.times(1)).enfileirar(any());
    }

    @Test
    void criarComInAppRepetidoNosCanaisAdicionaisNaoDuplicaALinha() {
        when(notificacaoRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(this::devolverOMesmoArgumento);

        List<Notificacao> criadas = service.criar(ambienteFinanceiroId, usuarioId, TipoNotificacao.FALHA_PROCESSAMENTO,
                "Titulo", "Mensagem", null, null, Set.of(CanalNotificacao.IN_APP, CanalNotificacao.PUSH));

        assertThat(criadas).hasSize(2);
        assertThat(criadas).extracting(Notificacao::getCanal).containsExactlyInAnyOrder(CanalNotificacao.IN_APP, CanalNotificacao.PUSH);
    }

    @Test
    void todasAsLinhasCriadasCompartilhamOMesmoConteudo() {
        ArgumentCaptor<Notificacao> capturado = ArgumentCaptor.forClass(Notificacao.class);
        when(notificacaoRepository.save(capturado.capture())).thenAnswer(this::devolverOMesmoArgumento);
        UUID referenciaId = UUID.randomUUID();

        service.criar(ambienteFinanceiroId, usuarioId, TipoNotificacao.VENCIMENTO_CONTA, "Conta vencendo",
                "Mensagem", "lancamento", referenciaId, Set.of(CanalNotificacao.EMAIL));

        for (Notificacao notificacao : capturado.getAllValues()) {
            assertThat(notificacao.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
            assertThat(notificacao.getUsuarioId()).isEqualTo(usuarioId);
            assertThat(notificacao.getTitulo()).isEqualTo("Conta vencendo");
            assertThat(notificacao.getReferenciaId()).isEqualTo(referenciaId);
            assertThat(notificacao.isLida()).isFalse();
        }
    }

    @Test
    void listarSemProximaPaginaNaoRetornaCursor() {
        Notificacao notificacao = Notificacao.novo(ambienteFinanceiroId, usuarioId, TipoNotificacao.VENCIMENTO_CONTA,
                "Titulo", "Mensagem", "lancamento", UUID.randomUUID(), CanalNotificacao.IN_APP);
        when(notificacaoRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(paginaCom(List.of(notificacao)));

        PaginaNotificacoes pagina = service.listar(contexto, null, null, null);

        assertThat(pagina.itens()).hasSize(1);
        assertThat(pagina.proximoCursor()).isNull();
    }

    @Test
    void listarComMaisResultadosQueOLimiteRetornaProximoCursorDecodavel() {
        Notificacao primeira = Notificacao.novo(ambienteFinanceiroId, usuarioId, TipoNotificacao.VENCIMENTO_CONTA,
                "Primeira", "Mensagem", null, null, CanalNotificacao.IN_APP);
        Notificacao segunda = Notificacao.novo(ambienteFinanceiroId, usuarioId, TipoNotificacao.VENCIMENTO_CONTA,
                "Segunda", "Mensagem", null, null, CanalNotificacao.IN_APP);
        when(notificacaoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(paginaCom(List.of(primeira, segunda)));

        PaginaNotificacoes pagina = service.listar(contexto, null, null, 1);

        assertThat(pagina.itens()).hasSize(1);
        assertThat(pagina.itens().get(0).titulo()).isEqualTo("Primeira");
        assertThat(pagina.proximoCursor()).isNotNull();
    }

    @Test
    void listarComLimiteForaDoIntervaloLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.listar(contexto, null, null, 0))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("LIMITE_INVALIDO"));

        assertThatThrownBy(() -> service.listar(contexto, null, null, 101))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("LIMITE_INVALIDO"));
    }

    @Test
    void listarComCursorInvalidoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.listar(contexto, null, "cursor-invalido-!!", null))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("CURSOR_INVALIDO"));
    }

    @Test
    void marcarComoLidaAtualizaAFlagQuandoEncontrada() {
        Notificacao notificacao = Notificacao.novo(ambienteFinanceiroId, usuarioId, TipoNotificacao.VENCIMENTO_CONTA,
                "Titulo", "Mensagem", null, null, CanalNotificacao.IN_APP);
        UUID notificacaoId = UUID.randomUUID();
        when(notificacaoRepository.findByIdAndUsuarioIdAndAmbienteFinanceiroId(notificacaoId, usuarioId, ambienteFinanceiroId))
                .thenReturn(Optional.of(notificacao));

        service.marcarComoLida(contexto, notificacaoId);

        assertThat(notificacao.isLida()).isTrue();
        verify(notificacaoRepository).save(notificacao);
    }

    @Test
    void marcarComoLidaDeNotificacaoInexistenteOuDeOutroUsuarioLancaRecursoNaoEncontrado() {
        UUID notificacaoId = UUID.randomUUID();
        when(notificacaoRepository.findByIdAndUsuarioIdAndAmbienteFinanceiroId(notificacaoId, usuarioId, ambienteFinanceiroId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marcarComoLida(contexto, notificacaoId))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private Page<Notificacao> paginaCom(List<Notificacao> itens) {
        return new PageImpl<>(itens);
    }

    private Notificacao devolverOMesmoArgumento(InvocationOnMock invocation) {
        return invocation.getArgument(0);
    }
}
