package br.com.unifinancas.application.notificacao;

import java.util.List;

/**
 * Porta para o envio efetivo de PUSH/EMAIL (SAD Sec7.6, Sec9.1) - IN_APP
 * nunca passa por aqui, e "entregue" so por existir na tabela {@code
 * notificacao} (T1.5.5). Implementada por um adapter de infraestrutura
 * (SMTP + Firebase Cloud Messaging, decisao consultada com o usuario,
 * T1.5.6) chamado a partir de {@code EnvioNotificacaoJobHandler}, nunca
 * diretamente de {@link NotificacaoService#criar} - o envio e assincrono
 * (fila de reenvio via {@code job_execucao}, ADR-005), sem bloquear o
 * fluxo que criou a Notificacao.
 */
public interface EnvioNotificacaoPort {

    void enviarEmail(String destinatario, String titulo, String mensagem);

    /** Um usuario pode ter mais de um dispositivo registrado - envia para todos de uma vez. */
    void enviarPush(List<String> tokensDispositivo, String titulo, String mensagem);
}
