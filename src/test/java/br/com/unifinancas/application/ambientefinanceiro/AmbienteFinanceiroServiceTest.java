package br.com.unifinancas.application.ambientefinanceiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiroRepository;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.StatusAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.StatusVinculo;
import br.com.unifinancas.domain.ambientefinanceiro.TipoAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AmbienteFinanceiroServiceTest {

    private final UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository =
            mock(UsuarioAmbienteFinanceiroRepository.class);
    private final AmbienteFinanceiroRepository ambienteFinanceiroRepository = mock(AmbienteFinanceiroRepository.class);
    private final AmbienteFinanceiroService service =
            new AmbienteFinanceiroService(usuarioAmbienteFinanceiroRepository, ambienteFinanceiroRepository);

    private final UUID usuarioId = UUID.randomUUID();

    @Test
    void listaSoOsAmbientesComVinculoAtivoComNomeTipoEPerfil() {
        UsuarioAmbienteFinanceiro vinculo =
                UsuarioAmbienteFinanceiro.primeiroVinculo(usuarioId, UUID.randomUUID());
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Familia Silva", TipoAmbienteFinanceiro.FAMILIAR);
        ReflectionTestUtils.setField(ambiente, "id", vinculo.getAmbienteFinanceiroId());

        when(usuarioAmbienteFinanceiroRepository.findAllByUsuarioIdAndStatus(usuarioId, StatusVinculo.ATIVO))
                .thenReturn(List.of(vinculo));
        when(ambienteFinanceiroRepository.findById(vinculo.getAmbienteFinanceiroId()))
                .thenReturn(Optional.of(ambiente));

        List<AmbienteFinanceiroResumo> resultado = service.listarAmbientesAtivosDoUsuario(usuarioId);

        assertThat(resultado).hasSize(1);
        AmbienteFinanceiroResumo resumo = resultado.get(0);
        assertThat(resumo.id()).isEqualTo(vinculo.getAmbienteFinanceiroId());
        assertThat(resumo.nome()).isEqualTo("Familia Silva");
        assertThat(resumo.tipo()).isEqualTo(TipoAmbienteFinanceiro.FAMILIAR);
        assertThat(resumo.perfil()).isEqualTo(PerfilUsuarioAmbiente.OWNER);
    }

    @Test
    void semNenhumVinculoAtivoRetornaListaVazia() {
        when(usuarioAmbienteFinanceiroRepository.findAllByUsuarioIdAndStatus(usuarioId, StatusVinculo.ATIVO))
                .thenReturn(List.of());

        assertThat(service.listarAmbientesAtivosDoUsuario(usuarioId)).isEmpty();
    }

    @Test
    void vinculoApontandoParaAmbienteInexistenteLancaIllegalState() {
        UsuarioAmbienteFinanceiro vinculo =
                UsuarioAmbienteFinanceiro.primeiroVinculo(usuarioId, UUID.randomUUID());
        when(usuarioAmbienteFinanceiroRepository.findAllByUsuarioIdAndStatus(usuarioId, StatusVinculo.ATIVO))
                .thenReturn(List.of(vinculo));
        when(ambienteFinanceiroRepository.findById(vinculo.getAmbienteFinanceiroId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listarAmbientesAtivosDoUsuario(usuarioId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void criarPersisteOAmbienteEOVinculoComoOwnerAtivo() {
        AmbienteFinanceiroResumo resumo = service.criar(usuarioId, "Projeto Paralelo", TipoAmbienteFinanceiro.PROJETO, null);

        var ambienteCapturado = org.mockito.ArgumentCaptor.forClass(AmbienteFinanceiro.class);
        verify(ambienteFinanceiroRepository).save(ambienteCapturado.capture());
        assertThat(ambienteCapturado.getValue().getNome()).isEqualTo("Projeto Paralelo");
        assertThat(ambienteCapturado.getValue().getTipo()).isEqualTo(TipoAmbienteFinanceiro.PROJETO);
        assertThat(ambienteCapturado.getValue().getDiasAntecedenciaNotificacaoVencimento()).isEqualTo(3);

        var vinculoCapturado = org.mockito.ArgumentCaptor.forClass(UsuarioAmbienteFinanceiro.class);
        verify(usuarioAmbienteFinanceiroRepository).save(vinculoCapturado.capture());
        assertThat(vinculoCapturado.getValue().getUsuarioId()).isEqualTo(usuarioId);
        assertThat(vinculoCapturado.getValue().getPerfil()).isEqualTo(PerfilUsuarioAmbiente.OWNER);
        assertThat(vinculoCapturado.getValue().estaAtivo()).isTrue();

        assertThat(resumo.nome()).isEqualTo("Projeto Paralelo");
        assertThat(resumo.tipo()).isEqualTo(TipoAmbienteFinanceiro.PROJETO);
        assertThat(resumo.perfil()).isEqualTo(PerfilUsuarioAmbiente.OWNER);
        assertThat(resumo.diasAntecedenciaNotificacaoVencimento()).isEqualTo(3);
    }

    @Test
    void criarComDiasAntecedenciaCustomizadoUsaOValorInformado() {
        AmbienteFinanceiroResumo resumo = service.criar(usuarioId, "Projeto Paralelo", TipoAmbienteFinanceiro.PROJETO, 7);

        assertThat(resumo.diasAntecedenciaNotificacaoVencimento()).isEqualTo(7);
    }

    /** Gap identificado pelo usuario: editar nao mexe no tipo, so nome/dias de antecedencia. */
    @Test
    void editarAlteraNomeEDiasDeAntecedenciaERetornaOResumoAtualizado() {
        UUID ambienteId = UUID.randomUUID();
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Familia Silva", TipoAmbienteFinanceiro.FAMILIAR);
        ReflectionTestUtils.setField(ambiente, "id", ambienteId);
        UsuarioAmbienteFinanceiro vinculo =
                UsuarioAmbienteFinanceiro.vincularComPerfil(usuarioId, ambienteId, PerfilUsuarioAmbiente.OWNER);

        when(ambienteFinanceiroRepository.findById(ambienteId)).thenReturn(Optional.of(ambiente));
        when(usuarioAmbienteFinanceiroRepository.findByUsuarioIdAndAmbienteFinanceiroId(usuarioId, ambienteId))
                .thenReturn(Optional.of(vinculo));

        TenantContext contexto = new TenantContext(usuarioId, ambienteId, PerfilUsuarioAmbiente.OWNER);
        AmbienteFinanceiroResumo resumo = service.editar(contexto, "Familia Silva Sobrenome", 10);

        assertThat(ambiente.getNome()).isEqualTo("Familia Silva Sobrenome");
        assertThat(ambiente.getDiasAntecedenciaNotificacaoVencimento()).isEqualTo(10);
        assertThat(resumo.nome()).isEqualTo("Familia Silva Sobrenome");
        assertThat(resumo.diasAntecedenciaNotificacaoVencimento()).isEqualTo(10);
        verify(ambienteFinanceiroRepository).save(ambiente);
    }

    /** Decisao consultada com o usuario: o Ambiente Pessoal criado no cadastro nunca pode ser excluido. */
    @Test
    void excluirDeAmbientePessoalLancaRegraDeNegocioENaoEncerraNada() {
        UUID ambienteId = UUID.randomUUID();
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Pessoal", TipoAmbienteFinanceiro.PESSOAL);
        ReflectionTestUtils.setField(ambiente, "id", ambienteId);
        when(ambienteFinanceiroRepository.findById(ambienteId)).thenReturn(Optional.of(ambiente));

        TenantContext contexto = new TenantContext(usuarioId, ambienteId, PerfilUsuarioAmbiente.OWNER);

        assertThatThrownBy(() -> service.excluir(contexto))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo())
                        .isEqualTo("AMBIENTE_PESSOAL_NAO_PODE_SER_EXCLUIDO"));
        assertThat(ambiente.getStatus()).isEqualTo(StatusAmbienteFinanceiro.ATIVO);
        verify(ambienteFinanceiroRepository, never()).save(ambiente);
    }

    /** Decisao consultada com o usuario: exclusao de um Ambiente compartilhado encerra ele e remove TODOS os membros. */
    @Test
    void excluirDeAmbienteFamiliarEncerraOAmbienteERemoveTodosOsMembros() {
        UUID ambienteId = UUID.randomUUID();
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo("Familia Silva", TipoAmbienteFinanceiro.FAMILIAR);
        ReflectionTestUtils.setField(ambiente, "id", ambienteId);
        when(ambienteFinanceiroRepository.findById(ambienteId)).thenReturn(Optional.of(ambiente));

        UsuarioAmbienteFinanceiro vinculoOwner =
                UsuarioAmbienteFinanceiro.vincularComPerfil(usuarioId, ambienteId, PerfilUsuarioAmbiente.OWNER);
        UsuarioAmbienteFinanceiro outroMembro =
                UsuarioAmbienteFinanceiro.vincularComPerfil(UUID.randomUUID(), ambienteId, PerfilUsuarioAmbiente.MEMBRO);
        when(usuarioAmbienteFinanceiroRepository.findAllByAmbienteFinanceiroIdAndStatus(ambienteId, StatusVinculo.ATIVO))
                .thenReturn(List.of(vinculoOwner, outroMembro));

        TenantContext contexto = new TenantContext(usuarioId, ambienteId, PerfilUsuarioAmbiente.OWNER);
        service.excluir(contexto);

        assertThat(ambiente.getStatus()).isEqualTo(StatusAmbienteFinanceiro.ENCERRADO);
        assertThat(vinculoOwner.getStatus()).isEqualTo(StatusVinculo.REMOVIDO);
        assertThat(outroMembro.getStatus()).isEqualTo(StatusVinculo.REMOVIDO);
        verify(ambienteFinanceiroRepository).save(ambiente);
    }
}
