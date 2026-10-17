package br.com.unifinancas.presentation.rest.notificacao;

import br.com.unifinancas.application.notificacao.DispositivoService;
import br.com.unifinancas.presentation.dto.RegistrarDispositivoRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * T1.5.6. Sem {@code X-Ambiente-Financeiro-Id}: um dispositivo pertence ao
 * Usuario (identidade global), nao a um Ambiente Financeiro - mesma
 * excecao ja aplicada a {@code GET/POST /v1/ambientes} (Sec12.4,
 * TenantContextFilter).
 */
@RestController
@RequestMapping("/v1/dispositivos")
public class DispositivoController {

    private final DispositivoService dispositivoService;

    public DispositivoController(DispositivoService dispositivoService) {
        this.dispositivoService = dispositivoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registrar(@AuthenticationPrincipal UUID usuarioId, @Valid @RequestBody RegistrarDispositivoRequest request) {
        dispositivoService.registrar(usuarioId, request.token(), request.plataforma());
    }
}
