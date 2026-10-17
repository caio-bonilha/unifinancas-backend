package br.com.unifinancas.application.notificacao;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** T1.5.6: Dispositivo (token de push) e do Usuario, identidade global - sem TenantContext (mesmo caso de EmissorTokenPort). */
@Service
public class DispositivoService {

    private final DispositivoPort dispositivoPort;

    public DispositivoService(DispositivoPort dispositivoPort) {
        this.dispositivoPort = dispositivoPort;
    }

    @Transactional
    public void registrar(UUID usuarioId, String token, PlataformaDispositivo plataforma) {
        dispositivoPort.registrar(usuarioId, token, plataforma);
    }
}
