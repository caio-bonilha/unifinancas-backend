package br.com.unifinancas.infrastructure.persistence.jpa;

/** Estados possiveis de um {@link JobExecucao} (SAD Sec5.2). */
public enum StatusJob {
    AGENDADO,
    EM_EXECUCAO,
    CONCLUIDO,
    ERRO
}
