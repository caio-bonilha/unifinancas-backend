package br.com.unifinancas.application.auditoria;

/**
 * Porta para persistir auditoria (SAD Sec9.1: "definicao de portas para
 * infraestrutura"). A tabela {@code auditoria} nao tem entidade de dominio
 * correspondente - e infraestrutura, como {@code job_execucao} - por isso a
 * porta vive em application (nao domain) e e implementada por um adapter JPA
 * em infrastructure.
 */
public interface AuditoriaPort {

    void registrar(RegistroAuditoria registro);
}
