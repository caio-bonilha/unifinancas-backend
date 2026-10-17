package br.com.unifinancas.infrastructure.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T1.3a.3, ADR-012: testa a migracao de dados retroativa (V21) isolada, sem
 * subir o contexto Spring - roda o Flyway diretamente ate a V20 (schema
 * anterior ao backfill), semeia Lancamentos via JDBC puro simulando dados
 * historicos, avanca ate a V21 e verifica as Movimentacoes geradas. Um
 * PostgreSQLContainer proprio (nao o {@code AbstractIntegrationTest}) porque
 * o objetivo e controlar o alvo do Flyway (target = "20" e depois "21"),
 * algo que o Spring Boot autoconfigurado (sempre "latest") nao permite.
 */
@Testcontainers
class MovimentacaoBackfillMigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Test
    void backfillCriaMovimentacoesDosLancamentosLiquidadosERemoveTransferencias() throws Exception {
        DataSource dataSource = new SingleConnectionDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), true);

        Flyway.configure().dataSource(dataSource).target("20").load().migrate();

        UUID ambienteId;
        UUID contaOrigemId;
        UUID contaDestinoId;
        UUID lancamentoDespesaId;
        UUID lancamentoReceitaId;
        UUID lancamentoTransferenciaId;
        UUID lancamentoPendenteId;

        try (Connection conexao = dataSource.getConnection()) {
            ambienteId = inserirAmbienteFinanceiro(conexao);
            contaOrigemId = inserirContaBancaria(conexao, ambienteId, "Conta Origem");
            contaDestinoId = inserirContaBancaria(conexao, ambienteId, "Conta Destino");

            lancamentoDespesaId = inserirLancamento(conexao, ambienteId, "DESPESA", "PAGO",
                    new BigDecimal("150.00"), contaOrigemId, null);
            lancamentoReceitaId = inserirLancamento(conexao, ambienteId, "RECEITA", "RECEBIDO",
                    new BigDecimal("300.00"), contaOrigemId, null);
            lancamentoTransferenciaId = inserirLancamento(conexao, ambienteId, "TRANSFERENCIA", "PAGO",
                    new BigDecimal("80.00"), contaOrigemId, contaDestinoId);
            lancamentoPendenteId = inserirLancamento(conexao, ambienteId, "TRANSFERENCIA", "PENDENTE",
                    new BigDecimal("40.00"), contaOrigemId, contaDestinoId);
        }

        Flyway.configure().dataSource(dataSource).target("21").load().migrate();

        try (Connection conexao = dataSource.getConnection()) {
            assertThat(buscarMovimentacao(conexao, lancamentoDespesaId))
                    .containsExactly("DEBITO", "150.00");
            assertThat(buscarMovimentacao(conexao, lancamentoReceitaId))
                    .containsExactly("CREDITO", "300.00");

            UUID grupoTransferencia = buscarGrupoTransferencia(conexao, contaOrigemId, contaDestinoId, "80.00");
            assertThat(grupoTransferencia).isNotNull();
            assertThat(contarMovimentacoesDoGrupo(conexao, grupoTransferencia)).isEqualTo(2);

            assertThat(lancamentoExiste(conexao, lancamentoTransferenciaId)).isFalse();
            assertThat(lancamentoExiste(conexao, lancamentoPendenteId)).isFalse();
            assertThat(lancamentoExiste(conexao, lancamentoDespesaId)).isTrue();
            assertThat(lancamentoExiste(conexao, lancamentoReceitaId)).isTrue();
        }
    }

    private UUID inserirAmbienteFinanceiro(Connection conexao) throws Exception {
        UUID id = UUID.randomUUID();
        try (PreparedStatement stmt = conexao.prepareStatement(
                "INSERT INTO ambiente_financeiro (id, nome, tipo, atualizado_em) "
                        + "VALUES (?, 'Ambiente de Teste', 'PESSOAL', now())")) {
            stmt.setObject(1, id);
            stmt.executeUpdate();
        }
        return id;
    }

    private UUID inserirContaBancaria(Connection conexao, UUID ambienteId, String nome) throws Exception {
        UUID id = UUID.randomUUID();
        try (PreparedStatement stmt = conexao.prepareStatement(
                "INSERT INTO conta_bancaria (id, ambiente_financeiro_id, nome, tipo, saldo_inicial, saldo_atual, moeda, ativa) "
                        + "VALUES (?, ?, ?, 'CORRENTE', 0, 0, 'BRL', true)")) {
            stmt.setObject(1, id);
            stmt.setObject(2, ambienteId);
            stmt.setString(3, nome);
            stmt.executeUpdate();
        }
        return id;
    }

    private UUID inserirLancamento(Connection conexao, UUID ambienteId, String tipo, String status, BigDecimal valor,
            UUID contaBancariaId, UUID contaBancariaDestinoId) throws Exception {
        UUID id = UUID.randomUUID();
        LocalDate hoje = LocalDate.now();
        try (PreparedStatement stmt = conexao.prepareStatement(
                "INSERT INTO lancamento (id, ambiente_financeiro_id, tipo, status, valor, data_emissao, "
                        + "data_vencimento, data_pagamento, conta_bancaria_id, conta_bancaria_destino_id, origem, descricao) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MANUAL', 'Lancamento de teste')")) {
            stmt.setObject(1, id);
            stmt.setObject(2, ambienteId);
            stmt.setString(3, tipo);
            stmt.setString(4, status);
            stmt.setBigDecimal(5, valor);
            stmt.setObject(6, hoje);
            stmt.setObject(7, hoje);
            stmt.setObject(8, hoje);
            stmt.setObject(9, contaBancariaId);
            stmt.setObject(10, contaBancariaDestinoId);
            stmt.executeUpdate();
        }
        return id;
    }

    private java.util.List<String> buscarMovimentacao(Connection conexao, UUID lancamentoId) throws Exception {
        try (PreparedStatement stmt = conexao.prepareStatement(
                "SELECT tipo, valor FROM movimentacao_conta_bancaria WHERE lancamento_id = ?")) {
            stmt.setObject(1, lancamentoId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return java.util.List.of(rs.getString("tipo"), rs.getBigDecimal("valor").toPlainString());
            }
        }
    }

    private UUID buscarGrupoTransferencia(Connection conexao, UUID contaOrigemId, UUID contaDestinoId, String valor)
            throws Exception {
        try (PreparedStatement stmt = conexao.prepareStatement(
                "SELECT grupo_transferencia_id FROM movimentacao_conta_bancaria "
                        + "WHERE conta_bancaria_id = ? AND tipo = 'DEBITO' AND valor = ?")) {
            stmt.setObject(1, contaOrigemId);
            stmt.setBigDecimal(2, new BigDecimal(valor));
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return (UUID) rs.getObject("grupo_transferencia_id");
            }
        }
    }

    private int contarMovimentacoesDoGrupo(Connection conexao, UUID grupoTransferenciaId) throws Exception {
        try (PreparedStatement stmt = conexao.prepareStatement(
                "SELECT COUNT(*) FROM movimentacao_conta_bancaria WHERE grupo_transferencia_id = ?")) {
            stmt.setObject(1, grupoTransferenciaId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private boolean lancamentoExiste(Connection conexao, UUID lancamentoId) throws Exception {
        try (PreparedStatement stmt = conexao.prepareStatement("SELECT 1 FROM lancamento WHERE id = ?")) {
            stmt.setObject(1, lancamentoId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
}
