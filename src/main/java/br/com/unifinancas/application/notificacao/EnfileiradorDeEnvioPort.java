package br.com.unifinancas.application.notificacao;

import java.util.UUID;

/**
 * Porta para enfileirar o envio assincrono de uma Notificacao PUSH/EMAIL
 * (T1.5.6) - implementada em infrastructure.jobs enfileirando uma linha em
 * {@code job_execucao} (ADR-005), despachada depois para {@code
 * EnvioNotificacaoJobHandler} pelo {@code JobWorker} ja existente (T0.3.3).
 * Chamada uma unica vez, no momento da criacao da Notificacao - nao ha
 * scanner periodico reenfileirando, o que evitaria duplicar jobs para a
 * mesma Notificacao ainda nao processada.
 */
public interface EnfileiradorDeEnvioPort {

    void enfileirar(UUID notificacaoId);
}
