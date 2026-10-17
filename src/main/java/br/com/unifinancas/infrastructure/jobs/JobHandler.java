package br.com.unifinancas.infrastructure.jobs;

import br.com.unifinancas.infrastructure.persistence.jpa.JobExecucao;

/**
 * Porta implementada por cada tipo de job assincrono (recorrencia,
 * notificacao, processamento de documento, importacao - SAD Sec3.4). O
 * {@link JobWorker} escolhe o handler certo pelo {@link #tipo()} de cada
 * {@link JobExecucao} reivindicado. Nenhum handler concreto existe ainda
 * nesta fase - virao com cada funcionalidade (ex.: RecorrenciaJob na T1.6.3).
 */
public interface JobHandler {

    /** Deve casar com o valor gravado em {@code job_execucao.tipo}. */
    String tipo();

    /** Lanca excecao para sinalizar falha - o {@link JobWorker} marca o job como ERRO. */
    void executar(JobExecucao job);
}
