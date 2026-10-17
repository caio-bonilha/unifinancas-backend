package br.com.unifinancas.presentation.rest.ambiente;

import br.com.unifinancas.application.ambientefinanceiro.MembroService;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.AcaoMembro;
import br.com.unifinancas.presentation.dto.AlterarMembroRequest;
import br.com.unifinancas.presentation.dto.MembroResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SAD §7.2, RF-04, §12.3. */
@RestController
@RequestMapping("/v1/ambientes/{id}/membros")
public class MembroController {

    private final MembroService membroService;

    public MembroController(MembroService membroService) {
        this.membroService = membroService;
    }

    /** Gap identificado na T1.1.8 (nao catalogado originalmente no SAD): sem listar, nao ha como escolher quem alterar/remover. */
    @GetMapping
    public List<MembroResponse> listar(@PathVariable UUID id, TenantContext contexto) {
        validarAmbienteDoPath(id, contexto);
        return membroService.listar(contexto).stream()
                .map(r -> new MembroResponse(r.usuarioId(), r.nome(), r.email(), r.perfil(), r.status()))
                .toList();
    }

    /** SAD §12.3: uma unica rota cobre alterar perfil ou remover, conforme {@code acao}. */
    @PatchMapping("/{usuarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterar(@PathVariable UUID id, @PathVariable UUID usuarioId, TenantContext contexto,
            @Valid @RequestBody AlterarMembroRequest request) {
        validarAmbienteDoPath(id, contexto);
        if (request.acao() == AcaoMembro.REMOVER) {
            membroService.remover(contexto, usuarioId);
        } else {
            if (request.perfil() == null) {
                throw new RegraDeNegocioException("PERFIL_OBRIGATORIO", "perfil e obrigatorio para a acao ALTERAR_PERFIL.");
            }
            membroService.alterarPerfil(contexto, usuarioId, request.perfil());
        }
    }

    private void validarAmbienteDoPath(UUID idPath, TenantContext contexto) {
        if (!idPath.equals(contexto.ambienteFinanceiroId())) {
            throw new RecursoNaoEncontradoException("AMBIENTE_NAO_ENCONTRADO", "Ambiente Financeiro nao encontrado.");
        }
    }
}
