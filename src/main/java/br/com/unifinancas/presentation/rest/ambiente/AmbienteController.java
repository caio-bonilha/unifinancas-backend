package br.com.unifinancas.presentation.rest.ambiente;

import br.com.unifinancas.application.ambientefinanceiro.AmbienteFinanceiroResumo;
import br.com.unifinancas.application.ambientefinanceiro.AmbienteFinanceiroService;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.AmbienteResponse;
import br.com.unifinancas.presentation.dto.CriarAmbienteRequest;
import br.com.unifinancas.presentation.dto.EditarAmbienteRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * SAD §7.2, RF-03. So exige autenticacao (usuario_id) - nao o header
 * X-Ambiente-Financeiro-Id, ao contrario de quase toda outra rota (ver
 * TenantContextFilter): o proposito daqui e listar/criar ambientes ANTES de
 * (ou sem precisar) ja ter um selecionado. "Trocar de ambiente ativo" nao
 * tem um endpoint proprio - e so o cliente passar a enviar outro id nesse
 * header nas proximas chamadas.
 */
@RestController
@RequestMapping("/v1/ambientes")
public class AmbienteController {

    private final AmbienteFinanceiroService ambienteFinanceiroService;

    public AmbienteController(AmbienteFinanceiroService ambienteFinanceiroService) {
        this.ambienteFinanceiroService = ambienteFinanceiroService;
    }

    @GetMapping
    public List<AmbienteResponse> listar(@AuthenticationPrincipal UUID usuarioId) {
        return ambienteFinanceiroService.listarAmbientesAtivosDoUsuario(usuarioId).stream()
                .map(this::paraResponse)
                .toList();
    }

    /** SAD §7.2: cria um novo Ambiente Financeiro - o usuario que cria vira OWNER (RN-03). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AmbienteResponse criar(@AuthenticationPrincipal UUID usuarioId, @Valid @RequestBody CriarAmbienteRequest request) {
        AmbienteFinanceiroResumo resumo = ambienteFinanceiroService.criar(
                usuarioId, request.nome(), request.tipo(), request.diasAntecedenciaNotificacaoVencimento());
        return paraResponse(resumo);
    }

    /**
     * Gap identificado pelo usuario - PUT /v1/ambientes/{id}: exige o header
     * X-Ambiente-Financeiro-Id (mesmo padrao de {@code MembroController}) -
     * so se edita o Ambiente ja selecionado, e so o OWNER (checado em
     * {@code AmbienteFinanceiroService.editar}).
     */
    @PutMapping("/{id}")
    public AmbienteResponse editar(@PathVariable UUID id, TenantContext contexto,
            @Valid @RequestBody EditarAmbienteRequest request) {
        validarAmbienteDoPath(id, contexto);
        return paraResponse(ambienteFinanceiroService.editar(contexto, request.nome(), request.diasAntecedenciaNotificacaoVencimento()));
    }

    /**
     * Gap identificado pelo usuario - DELETE /v1/ambientes/{id}: mesmas
     * regras de {@link #editar} quanto ao header e ao OWNER; o Ambiente
     * PESSOAL nunca pode ser excluido por aqui (checado em
     * {@code AmbienteFinanceiroService.excluir}).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id, TenantContext contexto) {
        validarAmbienteDoPath(id, contexto);
        ambienteFinanceiroService.excluir(contexto);
    }

    private void validarAmbienteDoPath(UUID idPath, TenantContext contexto) {
        if (!idPath.equals(contexto.ambienteFinanceiroId())) {
            throw new RecursoNaoEncontradoException("AMBIENTE_NAO_ENCONTRADO", "Ambiente Financeiro nao encontrado.");
        }
    }

    private AmbienteResponse paraResponse(AmbienteFinanceiroResumo resumo) {
        return new AmbienteResponse(resumo.id(), resumo.nome(), resumo.tipo(), resumo.perfil(),
                resumo.diasAntecedenciaNotificacaoVencimento());
    }
}
