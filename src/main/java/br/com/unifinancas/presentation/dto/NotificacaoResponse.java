package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.notificacao.CanalNotificacao;
import br.com.unifinancas.domain.notificacao.TipoNotificacao;
import java.time.Instant;
import java.util.UUID;

public record NotificacaoResponse(UUID id, TipoNotificacao tipo, String titulo, String mensagem,
        String referenciaTipo, UUID referenciaId, CanalNotificacao canal, boolean lida, Instant enviadaEm,
        Instant criadoEm) {
}
