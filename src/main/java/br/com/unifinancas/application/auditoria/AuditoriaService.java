package br.com.unifinancas.application.auditoria;

import br.com.unifinancas.domain.lancamento.LancamentoCriadoEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Registro de trilha de auditoria (SAD Sec3.4, Sec5.3, RNF-01, RNF-07). */
@Service
public class AuditoriaService {

    private final AuditoriaPort auditoriaPort;
    private final ObjectMapper objectMapper;

    public AuditoriaService(AuditoriaPort auditoriaPort, ObjectMapper objectMapper) {
        this.auditoriaPort = auditoriaPort;
        this.objectMapper = objectMapper;
    }

    public void registrar(RegistroAuditoria registro) {
        auditoriaPort.registrar(registro);
    }

    /**
     * RNF-07, Fluxo 6.2 (T1.3.7): registra a criacao de um Lancamento.
     * {@code @TransactionalEventListener} (fase AFTER_COMMIT, a default) -
     * o diagrama de sequencia do Fluxo 6.2 mostra essa reacao rodando "em
     * paralelo", depois que o 201 ja foi devolvido ao usuario, nunca dentro
     * da mesma transacao que persiste o Lancamento (Sec3.4/Sec13.5:
     * efeitos que nao sao o proposito principal da requisicao nao devem
     * segurar a transacao de banco). {@code @Transactional} aqui abre uma
     * transacao NOVA para o INSERT em auditoria (REQUIRES_NEW - Spring exige
     * isso explicitamente num listener AFTER_COMMIT, ja que a original ja
     * commitou e nao ha transacao ativa para "aderir").
     */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoLancamentoCriado(LancamentoCriadoEvent evento) {
        registrar(new RegistroAuditoria(evento.ambienteFinanceiroId(), evento.usuarioId(), "lancamento",
                evento.lancamentoId(), AcaoAuditoria.CRIACAO, null, serializar(evento)));
    }

    private String serializar(LancamentoCriadoEvent evento) {
        try {
            return objectMapper.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar LancamentoCriadoEvent para auditoria.", e);
        }
    }
}
