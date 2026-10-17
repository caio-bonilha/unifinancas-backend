package br.com.unifinancas.application.auditoria;

import java.util.UUID;

/**
 * Dados de uma linha de auditoria a ser gravada (SAD Sec5.2).
 * {@code ambienteFinanceiroId}/{@code usuarioId} sao nulos para acoes fora de
 * um tenant (ex.: login) ou automaticas do sistema, respectivamente.
 * {@code dadosAntes}/{@code dadosDepois} sao JSON (nulos quando nao aplicavel,
 * ex.: CRIACAO nao tem "antes").
 */
public record RegistroAuditoria(
        UUID ambienteFinanceiroId,
        UUID usuarioId,
        String entidade,
        UUID entidadeId,
        AcaoAuditoria acao,
        String dadosAntes,
        String dadosDepois) {
}
