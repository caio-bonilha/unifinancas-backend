package br.com.unifinancas.application.contato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.contato.Contato;
import br.com.unifinancas.domain.contato.ContatoRepository;
import br.com.unifinancas.domain.contato.TipoContato;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ContatoServiceTest {

    private final ContatoRepository contatoRepository = mock(ContatoRepository.class);
    private final ContatoService service = new ContatoService(contatoRepository);

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final TenantContext contexto = new TenantContext(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);

    @Test
    void listarRetornaOsContatosDoAmbienteDoContexto() {
        Contato contato = Contato.novo(ambienteFinanceiroId, "Cliente A", TipoContato.CLIENTE, null, null, null);
        when(contatoRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of(contato));

        List<ContatoResumo> resultado = service.listar(contexto);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nome()).isEqualTo("Cliente A");
        assertThat(resultado.get(0).tipo()).isEqualTo(TipoContato.CLIENTE);
    }

    @Test
    void semNenhumContatoRetornaListaVazia() {
        when(contatoRepository.findAllByAmbienteFinanceiroId(ambienteFinanceiroId)).thenReturn(List.of());

        assertThat(service.listar(contexto)).isEmpty();
    }

    @Test
    void criarPersisteOContatoNoAmbienteDoContexto() {
        ContatoResumo resumo = service.criar(contexto, "Fornecedor B", TipoContato.FORNECEDOR, "123", "b@example.com", "111");

        ArgumentCaptor<Contato> capturado = ArgumentCaptor.forClass(Contato.class);
        verify(contatoRepository).save(capturado.capture());
        assertThat(capturado.getValue().getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(capturado.getValue().getNome()).isEqualTo("Fornecedor B");
        assertThat(capturado.getValue().getDocumento()).isEqualTo("123");

        assertThat(resumo.nome()).isEqualTo("Fornecedor B");
        assertThat(resumo.ativo()).isTrue();
    }

    @Test
    void criarSemCamposOpcionaisFuncionaNormalmente() {
        ContatoResumo resumo = service.criar(contexto, "Cliente C", TipoContato.CLIENTE, null, null, null);

        assertThat(resumo.documento()).isNull();
        assertThat(resumo.email()).isNull();
        assertThat(resumo.telefone()).isNull();
    }

    @Test
    void editarAtualizaTodosOsCamposDescritivos() {
        UUID contatoId = UUID.randomUUID();
        Contato contato = comId(Contato.novo(ambienteFinanceiroId, "Antigo", TipoContato.CLIENTE, null, null, null), contatoId);
        when(contatoRepository.findByIdAndAmbienteFinanceiroId(contatoId, ambienteFinanceiroId)).thenReturn(Optional.of(contato));

        ContatoResumo resumo = service.editar(
                contexto, contatoId, "Novo Nome", TipoContato.FORNECEDOR, "999", "novo@example.com", "222");

        assertThat(resumo.nome()).isEqualTo("Novo Nome");
        assertThat(resumo.tipo()).isEqualTo(TipoContato.FORNECEDOR);
        assertThat(resumo.documento()).isEqualTo("999");
        assertThat(resumo.email()).isEqualTo("novo@example.com");
        assertThat(resumo.telefone()).isEqualTo("222");
        verify(contatoRepository).save(contato);
    }

    @Test
    void editarContatoInexistenteOuDeOutroAmbienteLancaRecursoNaoEncontrado() {
        UUID contatoId = UUID.randomUUID();
        when(contatoRepository.findByIdAndAmbienteFinanceiroId(contatoId, ambienteFinanceiroId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.editar(contexto, contatoId, "X", TipoContato.CLIENTE, null, null, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void desativarEAtivarAlternamOFlag() {
        UUID contatoId = UUID.randomUUID();
        Contato contato = comId(Contato.novo(ambienteFinanceiroId, "Contato", TipoContato.CLIENTE, null, null, null), contatoId);
        when(contatoRepository.findByIdAndAmbienteFinanceiroId(contatoId, ambienteFinanceiroId)).thenReturn(Optional.of(contato));

        ContatoResumo desativado = service.desativar(contexto, contatoId);
        assertThat(desativado.ativo()).isFalse();

        ContatoResumo reativado = service.ativar(contexto, contatoId);
        assertThat(reativado.ativo()).isTrue();
    }

    private Contato comId(Contato contato, UUID id) {
        ReflectionTestUtils.setField(contato, "id", id);
        return contato;
    }
}
