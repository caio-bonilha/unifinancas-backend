package br.com.unifinancas.presentation.rest.ambiente;

import br.com.unifinancas.application.ambientefinanceiro.ConviteResumo;
import br.com.unifinancas.application.ambientefinanceiro.ConviteService;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.AceitarConviteRequest;
import br.com.unifinancas.presentation.dto.ConvidarRequest;
import br.com.unifinancas.presentation.dto.ConviteResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * SAD §7.2, RF-04, §12.5. {@code aceitar} nao recebe {@link TenantContext}
 * como parametro: e uma das rotas isentas do header
 * X-Ambiente-Financeiro-Id no {@code TenantContextFilter} - quem aceita
 * ainda nao tem vinculo com o ambiente do convite.
 */
@RestController
@RequestMapping("/v1/ambientes/{id}/convites")
public class ConviteController {

    private final ConviteService conviteService;

    public ConviteController(ConviteService conviteService) {
        this.conviteService = conviteService;
    }

    /** SAD §12.3: convidar exige OWNER ou ADMIN (checado por @RequerPerfil no ConviteService). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConviteResponse convidar(@PathVariable UUID id, TenantContext contexto,
            @Valid @RequestBody ConvidarRequest request) {
        validarAmbienteDoPath(id, contexto);
        ConviteResumo resumo = conviteService.convidar(contexto, request.email(), request.perfil());
        return new ConviteResponse(
                resumo.id(), resumo.email(), resumo.perfil(), resumo.status(), resumo.expiraEm(), resumo.token());
    }

    /** SAD §12.5: aceite por quem ja tinha conta no momento do convite. */
    @PostMapping("/aceitar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void aceitar(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID id,
            @Valid @RequestBody AceitarConviteRequest request) {
        conviteService.aceitar(id, usuarioId, request.token());
    }

    private void validarAmbienteDoPath(UUID idPath, TenantContext contexto) {
        if (!idPath.equals(contexto.ambienteFinanceiroId())) {
            throw new RecursoNaoEncontradoException("AMBIENTE_NAO_ENCONTRADO", "Ambiente Financeiro nao encontrado.");
        }
    }
}
