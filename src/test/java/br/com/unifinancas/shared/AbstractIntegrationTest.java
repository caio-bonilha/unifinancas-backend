package br.com.unifinancas.shared;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base para testes de integracao que precisam de um PostgreSQL real
 * (SAD Sec15.2) - evita a divergencia "funciona no H2, quebra no Postgres".
 * O datasource da aplicacao e conectado automaticamente ao container via
 * {@code @ServiceConnection}, sem propriedades manuais.
 */
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");
}
