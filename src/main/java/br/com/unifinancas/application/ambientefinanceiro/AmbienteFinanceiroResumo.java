package br.com.unifinancas.application.ambientefinanceiro;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.TipoAmbienteFinanceiro;
import java.util.UUID;

/** Um Ambiente Financeiro que o usuario pode escolher (SAD RF-03, T1.1.7). */
public record AmbienteFinanceiroResumo(UUID id, String nome, TipoAmbienteFinanceiro tipo, PerfilUsuarioAmbiente perfil,
        int diasAntecedenciaNotificacaoVencimento) {
}
