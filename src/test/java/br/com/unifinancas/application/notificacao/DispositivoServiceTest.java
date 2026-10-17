package br.com.unifinancas.application.notificacao;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispositivoServiceTest {

    private final DispositivoPort dispositivoPort = mock(DispositivoPort.class);
    private final DispositivoService service = new DispositivoService(dispositivoPort);

    @Test
    void registrarDelegaParaOPort() {
        UUID usuarioId = UUID.randomUUID();

        service.registrar(usuarioId, "token-abc", PlataformaDispositivo.ANDROID);

        verify(dispositivoPort).registrar(usuarioId, "token-abc", PlataformaDispositivo.ANDROID);
    }
}
