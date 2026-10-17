package br.com.unifinancas.domain.ambientefinanceiro;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UsuarioAmbienteFinanceiroTest {

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID ambienteFinanceiroId = UUID.randomUUID();

    @Test
    void rn03_primeiroVinculoDeUmAmbienteEsempreOwnerEJaNasceAtivo() {
        UsuarioAmbienteFinanceiro vinculo = UsuarioAmbienteFinanceiro.primeiroVinculo(usuarioId, ambienteFinanceiroId);

        assertThat(vinculo.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(vinculo.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(vinculo.getPerfil()).isEqualTo(PerfilUsuarioAmbiente.OWNER);
        assertThat(vinculo.getStatus()).isEqualTo(StatusVinculo.ATIVO);
        assertThat(vinculo.getDataEntrada()).isNotNull();
    }

    @Test
    void vincularComPerfilPermiteEscolherOPerfilEJaNasceAtivo() {
        UsuarioAmbienteFinanceiro vinculo =
                UsuarioAmbienteFinanceiro.vincularComPerfil(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);

        assertThat(vinculo.getPerfil()).isEqualTo(PerfilUsuarioAmbiente.MEMBRO);
        assertThat(vinculo.getStatus()).isEqualTo(StatusVinculo.ATIVO);
    }

    @Test
    void rn04_estaAtivoSoRetornaVerdadeiroQuandoOStatusEAtivo() {
        UsuarioAmbienteFinanceiro owner = UsuarioAmbienteFinanceiro.primeiroVinculo(usuarioId, ambienteFinanceiroId);
        UsuarioAmbienteFinanceiro removido =
                UsuarioAmbienteFinanceiro.vincularComPerfil(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.VISUALIZADOR);
        removido.remover();

        assertThat(owner.estaAtivo()).isTrue();
        assertThat(removido.estaAtivo()).isFalse();
    }

    @Test
    void alterarPerfilTrocaOPerfilDoVinculo() {
        UsuarioAmbienteFinanceiro vinculo =
                UsuarioAmbienteFinanceiro.vincularComPerfil(usuarioId, ambienteFinanceiroId, PerfilUsuarioAmbiente.MEMBRO);

        vinculo.alterarPerfil(PerfilUsuarioAmbiente.ADMIN);

        assertThat(vinculo.getPerfil()).isEqualTo(PerfilUsuarioAmbiente.ADMIN);
    }

    @Test
    void removerMudaOStatusParaRemovido() {
        UsuarioAmbienteFinanceiro vinculo = UsuarioAmbienteFinanceiro.primeiroVinculo(usuarioId, ambienteFinanceiroId);

        vinculo.remover();

        assertThat(vinculo.getStatus()).isEqualTo(StatusVinculo.REMOVIDO);
        assertThat(vinculo.estaAtivo()).isFalse();
    }
}
