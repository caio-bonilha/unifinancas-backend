package br.com.unifinancas.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobExecucaoRepository extends JpaRepository<JobExecucao, UUID> {

    /**
     * Reivindica ate {@code quantidade} jobs pendentes usando
     * {@code FOR UPDATE SKIP LOCKED} (SAD Sec16.5, ADR-005) - permite mais de
     * uma instancia do worker rodando ao mesmo tempo sem que duas peguem o
     * mesmo job: quem ja tem o lock de uma linha faz as demais instancias
     * pularem essa linha em vez de esperar. Precisa rodar dentro de uma
     * transacao para o lock ser mantido ate o commit.
     */
    @Query(value = """
            SELECT * FROM job_execucao
            WHERE status = 'AGENDADO' AND agendado_para <= now()
            ORDER BY agendado_para
            LIMIT :quantidade
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<JobExecucao> buscarProximosParaExecutar(@Param("quantidade") int quantidade);
}
