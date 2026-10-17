package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.ambientefinanceiro.TipoAmbienteFinanceiro;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** SAD §7.2, RNF-09 (LGPD - portabilidade de dados, T1.11.4). */
public record ExportacaoDadosResponse(UsuarioExportadoResponse usuario, List<AmbienteExportadoResponse> ambientes) {

    public record UsuarioExportadoResponse(UUID id, String nome, String email, Instant criadoEm) {
    }

    public record AmbienteExportadoResponse(
            UUID id, String nome, TipoAmbienteFinanceiro tipo,
            List<ContaBancariaResponse> contasBancarias,
            List<CategoriaResponse> categorias,
            List<ContatoResponse> contatos,
            List<LancamentoResponse> lancamentos,
            List<LancamentoRecorrenteResponse> lancamentosRecorrentes,
            List<MovimentacaoResponse> movimentacoes,
            List<OrcamentoMetaResponse> orcamentosMetas) {
    }
}
