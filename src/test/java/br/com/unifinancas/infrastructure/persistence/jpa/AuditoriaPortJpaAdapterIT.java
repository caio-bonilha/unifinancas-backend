package br.com.unifinancas.infrastructure.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.unifinancas.application.auditoria.AcaoAuditoria;
import br.com.unifinancas.application.auditoria.RegistroAuditoria;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Valida contra um Postgres real (Testcontainers) que o adapter grava a linha corretamente. */
class AuditoriaPortJpaAdapterIT extends AbstractIntegrationTest {

    @Autowired
    private AuditoriaPortJpaAdapter adapter;

    @Autowired
    private AuditoriaJpaRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registrarPersisteALinhaComTodosOsCampos() throws Exception {
        UUID ambienteFinanceiroId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        UUID entidadeId = UUID.randomUUID();
        RegistroAuditoria registro = new RegistroAuditoria(
                ambienteFinanceiroId, usuarioId, "lancamento", entidadeId,
                AcaoAuditoria.ALTERACAO, "{\"status\":\"PENDENTE\"}", "{\"status\":\"PAGO\"}");

        adapter.registrar(registro);

        List<Auditoria> todas = repository.findAll();
        assertThat(todas).hasSize(1);
        Auditoria salva = todas.get(0);
        assertThat(salva.getId()).isNotNull();
        assertThat(salva.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(salva.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(salva.getEntidade()).isEqualTo("lancamento");
        assertThat(salva.getEntidadeId()).isEqualTo(entidadeId);
        assertThat(salva.getAcao()).isEqualTo(AcaoAuditoria.ALTERACAO);
        assertThat(objectMapper.readTree(salva.getDadosAntes())).isEqualTo(objectMapper.readTree(registro.dadosAntes()));
        assertThat(objectMapper.readTree(salva.getDadosDepois())).isEqualTo(objectMapper.readTree(registro.dadosDepois()));
        assertThat(salva.getCriadoEm()).isNotNull();
    }
}
