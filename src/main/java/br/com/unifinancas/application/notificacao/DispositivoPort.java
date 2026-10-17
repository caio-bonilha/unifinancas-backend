package br.com.unifinancas.application.notificacao;

import java.util.List;
import java.util.UUID;

/**
 * Porta para o registro de tokens de dispositivo (SAD Sec9.1) - a tabela
 * {@code dispositivo_usuario} nao tem entidade de dominio correspondente,
 * mesmo caso de {@code RefreshToken}: e infraestrutura de suporte ao envio
 * de PUSH (T1.5.6), implementada por um adapter JPA em infrastructure.
 */
public interface DispositivoPort {

    /** Idempotente: registrar o mesmo token de novo apenas reatribui o usuario/plataforma (reinstalacao, troca de conta no mesmo aparelho). */
    void registrar(UUID usuarioId, String token, PlataformaDispositivo plataforma);

    List<String> buscarTokensDoUsuario(UUID usuarioId);
}
