package br.com.unifinancas.presentation.rest.contato;

import br.com.unifinancas.application.contato.ContatoResumo;
import br.com.unifinancas.application.contato.ContatoService;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.AtualizarContatoRequest;
import br.com.unifinancas.presentation.dto.ContatoResponse;
import br.com.unifinancas.presentation.dto.CriarContatoRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SAD §7.2, RF-10. */
@RestController
@RequestMapping("/v1/contatos")
public class ContatoController {

    private final ContatoService contatoService;

    public ContatoController(ContatoService contatoService) {
        this.contatoService = contatoService;
    }

    @GetMapping
    public List<ContatoResponse> listar(TenantContext contexto) {
        return contatoService.listar(contexto).stream().map(this::paraResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContatoResponse criar(TenantContext contexto, @Valid @RequestBody CriarContatoRequest request) {
        ContatoResumo resumo = contatoService.criar(
                contexto, request.nome(), request.tipo(), request.documento(), request.email(), request.telefone());
        return paraResponse(resumo);
    }

    /** T1.9.6 (gap identificado ao planejar a tela): uma unica rota cobre editar, ativar e desativar. */
    @PatchMapping("/{id}")
    public ContatoResponse atualizar(TenantContext contexto, @PathVariable UUID id,
            @Valid @RequestBody AtualizarContatoRequest request) {
        ContatoResumo resumo = switch (request.acao()) {
            case EDITAR -> {
                if (request.nome() == null || request.tipo() == null) {
                    throw new RegraDeNegocioException(
                            "CAMPOS_OBRIGATORIOS_PARA_EDITAR", "nome e tipo sao obrigatorios para a acao EDITAR.");
                }
                yield contatoService.editar(contexto, id, request.nome(), request.tipo(), request.documento(),
                        request.email(), request.telefone());
            }
            case ATIVAR -> contatoService.ativar(contexto, id);
            case DESATIVAR -> contatoService.desativar(contexto, id);
        };
        return paraResponse(resumo);
    }

    private ContatoResponse paraResponse(ContatoResumo resumo) {
        return new ContatoResponse(resumo.id(), resumo.nome(), resumo.tipo(), resumo.documento(), resumo.email(),
                resumo.telefone(), resumo.ativo());
    }
}
