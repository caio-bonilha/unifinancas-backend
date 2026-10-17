package br.com.unifinancas.domain.lancamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio publicado (Spring {@code ApplicationEventPublisher},
 * SAD Sec3.4) logo apos um Lancamento MANUAL ser criado e persistido
 * (Fluxo 6.2) - permite que outros modulos reajam sem acoplamento direto
 * a {@code LancamentoService}: {@code OrcamentoMetaService} reavalia o
 * orcamento da categoria (T1.5.3), {@code AuditoriaService} registra a
 * criacao (T1.3.7). Carrega um snapshot de valores simples, nao a entidade
 * viva - consumidores nao dependem do ciclo de vida da transacao/sessao
 * JPA que a criou.
 */
public record LancamentoCriadoEvent(
        UUID lancamentoId, UUID ambienteFinanceiroId, UUID categoriaId, UUID usuarioId, TipoLancamento tipo,
        BigDecimal valor, Instant ocorridoEm) {

    public static LancamentoCriadoEvent de(LancamentoFinanceiro lancamento, UUID usuarioId) {
        return new LancamentoCriadoEvent(lancamento.getId(), lancamento.getAmbienteFinanceiroId(),
                lancamento.getCategoriaId(), usuarioId, lancamento.getTipo(), lancamento.getValor(), Instant.now());
    }
}
