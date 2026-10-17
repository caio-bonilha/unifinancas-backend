package br.com.unifinancas.application.contato;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.contato.Contato;
import br.com.unifinancas.domain.contato.ContatoRepository;
import br.com.unifinancas.domain.contato.TipoContato;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-10, SAD Sec4.12: cadastro de Contato (cliente/fornecedor) de um Ambiente Financeiro. */
@Service
public class ContatoService {

    private final ContatoRepository contatoRepository;

    public ContatoService(ContatoRepository contatoRepository) {
        this.contatoRepository = contatoRepository;
    }

    /** SAD Sec8.2: leitura de dados financeiros e permitida a qualquer perfil, inclusive VISUALIZADOR. */
    public List<ContatoResumo> listar(TenantContext contexto) {
        return contatoRepository.findAllByAmbienteFinanceiroId(contexto.ambienteFinanceiroId()).stream()
                .map(this::paraResumo)
                .toList();
    }

    /** SAD Sec8.2: escrita financeira exige OWNER, ADMIN ou MEMBRO - VISUALIZADOR nao pode. */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public ContatoResumo criar(TenantContext contexto, String nome, TipoContato tipo, String documento, String email,
            String telefone) {
        Contato contato = Contato.novo(contexto.ambienteFinanceiroId(), nome, tipo, documento, email, telefone);
        contatoRepository.save(contato);
        return paraResumo(contato);
    }

    /** SAD Sec8.2: escrita financeira exige OWNER, ADMIN ou MEMBRO. */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public ContatoResumo editar(TenantContext contexto, UUID id, String nome, TipoContato tipo, String documento,
            String email, String telefone) {
        Contato contato = buscarOuFalhar(contexto, id);
        contato.editar(nome, tipo, documento, email, telefone);
        contatoRepository.save(contato);
        return paraResumo(contato);
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public ContatoResumo ativar(TenantContext contexto, UUID id) {
        Contato contato = buscarOuFalhar(contexto, id);
        contato.ativar();
        contatoRepository.save(contato);
        return paraResumo(contato);
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public ContatoResumo desativar(TenantContext contexto, UUID id) {
        Contato contato = buscarOuFalhar(contexto, id);
        contato.desativar();
        contatoRepository.save(contato);
        return paraResumo(contato);
    }

    private Contato buscarOuFalhar(TenantContext contexto, UUID id) {
        return contatoRepository.findByIdAndAmbienteFinanceiroId(id, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CONTATO_NAO_ENCONTRADO", "Contato nao encontrado neste Ambiente Financeiro."));
    }

    private ContatoResumo paraResumo(Contato contato) {
        return new ContatoResumo(contato.getId(), contato.getNome(), contato.getTipo(), contato.getDocumento(),
                contato.getEmail(), contato.getTelefone(), contato.isAtivo());
    }
}
