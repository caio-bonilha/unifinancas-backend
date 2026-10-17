package br.com.unifinancas.presentation.rest.notificacao;

import br.com.unifinancas.application.notificacao.NotificacaoResumo;
import br.com.unifinancas.application.notificacao.NotificacaoService;
import br.com.unifinancas.application.notificacao.PaginaNotificacoes;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.NotificacaoResponse;
import br.com.unifinancas.presentation.dto.PaginaNotificacoesResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SAD §7.2, RF-14. */
@RestController
@RequestMapping("/v1/notificacoes")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    public NotificacaoController(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @GetMapping
    public PaginaNotificacoesResponse listar(TenantContext contexto, @RequestParam(required = false) Boolean lida,
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        PaginaNotificacoes pagina = notificacaoService.listar(contexto, lida, cursor, limit);
        return new PaginaNotificacoesResponse(pagina.itens().stream().map(this::paraResponse).toList(), pagina.proximoCursor());
    }

    @PatchMapping("/{id}/lida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarComoLida(TenantContext contexto, @PathVariable UUID id) {
        notificacaoService.marcarComoLida(contexto, id);
    }

    private NotificacaoResponse paraResponse(NotificacaoResumo resumo) {
        return new NotificacaoResponse(resumo.id(), resumo.tipo(), resumo.titulo(), resumo.mensagem(),
                resumo.referenciaTipo(), resumo.referenciaId(), resumo.canal(), resumo.lida(), resumo.enviadaEm(),
                resumo.criadoEm());
    }
}
