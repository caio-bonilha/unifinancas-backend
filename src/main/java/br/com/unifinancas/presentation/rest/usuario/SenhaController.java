package br.com.unifinancas.presentation.rest.usuario;

import br.com.unifinancas.application.usuario.IdentidadeService;
import br.com.unifinancas.presentation.dto.AlterarSenhaRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * SAD §7.2, §6.8, RN-14 (T1.1.10): alteracao de senha pelo usuario logado.
 * So exige autenticacao (usuario_id do access token), nao o header
 * X-Ambiente-Financeiro-Id - a senha pertence a identidade global do
 * Usuario, nao a um Ambiente Financeiro (mesma excecao de
 * {@code /v1/usuarios/me}, ver {@code TenantContextFilter}).
 */
@RestController
@RequestMapping("/v1/usuarios/me/senha")
public class SenhaController {

    private final IdentidadeService identidadeService;

    public SenhaController(IdentidadeService identidadeService) {
        this.identidadeService = identidadeService;
    }

    /** Exige a senha atual correta (422 SENHA_ATUAL_INCORRETA); revoga as demais sessoes do Usuario. */
    @PatchMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterarSenha(@AuthenticationPrincipal UUID usuarioId, @Valid @RequestBody AlterarSenhaRequest request) {
        identidadeService.alterarSenha(usuarioId, request.senhaAtual(), request.novaSenha());
    }
}
