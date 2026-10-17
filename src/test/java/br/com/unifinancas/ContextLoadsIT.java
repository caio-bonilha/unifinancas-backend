package br.com.unifinancas;

import br.com.unifinancas.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Smoke test que sobe o contexto Spring completo contra um PostgreSQL real
 * via Testcontainers, aplicando a migracao Flyway - valida que
 * JUnit5 + Mockito + Testcontainers estao configurados corretamente (T0.2.2).
 *
 * Sufixo IT (nao Test): roda via Failsafe na fase de integration-test/verify,
 * separado dos testes unitarios do Surefire (SAD Sec15.1 vs Sec15.2 e o
 * pipeline de CI da Sec16.2, que trata "testes unitarios" e "testes de
 * integracao com Testcontainers" como estagios distintos).
 */
class ContextLoadsIT extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
