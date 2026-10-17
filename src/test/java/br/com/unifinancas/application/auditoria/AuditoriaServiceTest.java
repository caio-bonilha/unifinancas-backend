package br.com.unifinancas.application.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.unifinancas.domain.lancamento.LancamentoCriadoEvent;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditoriaServiceTest {

    private final AuditoriaPort auditoriaPort = mock(AuditoriaPort.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AuditoriaService service = new AuditoriaService(auditoriaPort, objectMapper);

    @Test
    void registrarDelegaParaAPorta() {
        RegistroAuditoria registro = new RegistroAuditoria(
                UUID.randomUUID(), UUID.randomUUID(), "lancamento", UUID.randomUUID(),
                AcaoAuditoria.CRIACAO, null, "{\"valor\":100}");

        service.registrar(registro);

        verify(auditoriaPort).registrar(registro);
    }

    @Test
    void aoLancamentoCriadoRegistraAuditoriaDeCriacaoComOsDadosDoEvento() {
        LancamentoCriadoEvent evento = new LancamentoCriadoEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), TipoLancamento.DESPESA, new BigDecimal("100.00"), Instant.now());

        service.aoLancamentoCriado(evento);

        ArgumentCaptor<RegistroAuditoria> capturado = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(auditoriaPort).registrar(capturado.capture());
        RegistroAuditoria registro = capturado.getValue();
        assertThat(registro.ambienteFinanceiroId()).isEqualTo(evento.ambienteFinanceiroId());
        assertThat(registro.usuarioId()).isEqualTo(evento.usuarioId());
        assertThat(registro.entidade()).isEqualTo("lancamento");
        assertThat(registro.entidadeId()).isEqualTo(evento.lancamentoId());
        assertThat(registro.acao()).isEqualTo(AcaoAuditoria.CRIACAO);
        assertThat(registro.dadosAntes()).isNull();
        assertThat(registro.dadosDepois()).contains("100.00").contains("DESPESA");
    }
}
