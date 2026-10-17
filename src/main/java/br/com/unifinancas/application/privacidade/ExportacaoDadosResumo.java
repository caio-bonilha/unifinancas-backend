package br.com.unifinancas.application.privacidade;

import br.com.unifinancas.application.categoria.CategoriaResumo;
import br.com.unifinancas.application.contabancaria.ContaBancariaResumo;
import br.com.unifinancas.application.contato.ContatoResumo;
import br.com.unifinancas.application.lancamento.LancamentoResumo;
import br.com.unifinancas.application.lancamentorecorrente.LancamentoRecorrenteResumo;
import br.com.unifinancas.application.movimentacao.MovimentacaoResumo;
import br.com.unifinancas.application.orcamentometa.OrcamentoMetaResumo;
import br.com.unifinancas.domain.ambientefinanceiro.TipoAmbienteFinanceiro;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * T1.11.4, RNF-09 (LGPD - portabilidade de dados, decisao consultada com o
 * usuario): so os Ambientes Financeiros onde o usuario e OWNER entram no
 * export - um Ambiente FAMILIAR/PROJETO onde ele e so MEMBRO/VISUALIZADOR
 * contem dados de outras pessoas tambem, e exportar tudo dali exporia
 * informacao financeira de terceiros sem o consentimento deles. Dentro de
 * cada Ambiente exportado, os dados sao o dump bruto das entidades
 * (nao os campos computados sob demanda - RN-10, ex.: progresso de
 * Orcamento/Meta - {@code valorAtual}/{@code percentualAtingido} vem nulos
 * aqui, porque nao sao dado armazenado, sao calculo de tela). Documento
 * (so metadados do RF-16) e Notificacao ficam fora desta primeira versao do
 * export - nao sao o nucleo do que "portabilidade" normalmente cobre para
 * este produto (SAD Sec19, revisar se algum dia virar um requisito real).
 */
public record ExportacaoDadosResumo(UsuarioExportado usuario, List<AmbienteExportado> ambientes) {

    public record UsuarioExportado(UUID id, String nome, String email, Instant criadoEm) {
    }

    public record AmbienteExportado(
            UUID id, String nome, TipoAmbienteFinanceiro tipo,
            List<ContaBancariaResumo> contasBancarias,
            List<CategoriaResumo> categorias,
            List<ContatoResumo> contatos,
            List<LancamentoResumo> lancamentos,
            List<LancamentoRecorrenteResumo> lancamentosRecorrentes,
            List<MovimentacaoResumo> movimentacoes,
            List<OrcamentoMetaResumo> orcamentosMetas) {
    }
}
