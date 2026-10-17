package br.com.unifinancas.presentation.rest.usuario;

import br.com.unifinancas.application.categoria.CategoriaResumo;
import br.com.unifinancas.application.contabancaria.ContaBancariaResumo;
import br.com.unifinancas.application.contato.ContatoResumo;
import br.com.unifinancas.application.lancamento.LancamentoResumo;
import br.com.unifinancas.application.lancamentorecorrente.LancamentoRecorrenteResumo;
import br.com.unifinancas.application.movimentacao.MovimentacaoResumo;
import br.com.unifinancas.application.orcamentometa.OrcamentoMetaResumo;
import br.com.unifinancas.application.privacidade.ExportacaoDadosResumo;
import br.com.unifinancas.application.privacidade.PrivacidadeService;
import br.com.unifinancas.presentation.dto.CategoriaResponse;
import br.com.unifinancas.presentation.dto.ContaBancariaResponse;
import br.com.unifinancas.presentation.dto.ContatoResponse;
import br.com.unifinancas.presentation.dto.ExportacaoDadosResponse;
import br.com.unifinancas.presentation.dto.ExportacaoDadosResponse.AmbienteExportadoResponse;
import br.com.unifinancas.presentation.dto.ExportacaoDadosResponse.UsuarioExportadoResponse;
import br.com.unifinancas.presentation.dto.LancamentoRecorrenteResponse;
import br.com.unifinancas.presentation.dto.LancamentoResponse;
import br.com.unifinancas.presentation.dto.MovimentacaoResponse;
import br.com.unifinancas.presentation.dto.OrcamentoMetaResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * SAD §7.2, RNF-09 (LGPD - portabilidade e direito ao esquecimento, T1.11.4).
 * So exige autenticacao (usuario_id), nao o header X-Ambiente-Financeiro-Id
 * (ver TenantContextFilter) - as duas acoes atravessam todos os Ambientes
 * Financeiros do usuario, nao um so.
 */
@RestController
@RequestMapping("/v1/usuarios/me")
public class PrivacidadeController {

    private final PrivacidadeService privacidadeService;

    public PrivacidadeController(PrivacidadeService privacidadeService) {
        this.privacidadeService = privacidadeService;
    }

    @GetMapping("/exportacao")
    public ExportacaoDadosResponse exportarDados(@AuthenticationPrincipal UUID usuarioId) {
        return paraResponse(privacidadeService.exportarDados(usuarioId));
    }

    /** RN-13: soft-delete + anonimizacao - nunca remocao fisica imediata (Sec4.1). */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirConta(@AuthenticationPrincipal UUID usuarioId) {
        privacidadeService.excluirConta(usuarioId);
    }

    private ExportacaoDadosResponse paraResponse(ExportacaoDadosResumo resumo) {
        UsuarioExportadoResponse usuario = new UsuarioExportadoResponse(resumo.usuario().id(), resumo.usuario().nome(),
                resumo.usuario().email(), resumo.usuario().criadoEm());
        return new ExportacaoDadosResponse(usuario, resumo.ambientes().stream().map(this::paraResponse).toList());
    }

    private AmbienteExportadoResponse paraResponse(ExportacaoDadosResumo.AmbienteExportado ambiente) {
        return new AmbienteExportadoResponse(ambiente.id(), ambiente.nome(), ambiente.tipo(),
                ambiente.contasBancarias().stream().map(this::paraResponse).toList(),
                ambiente.categorias().stream().map(this::paraResponse).toList(),
                ambiente.contatos().stream().map(this::paraResponse).toList(),
                ambiente.lancamentos().stream().map(this::paraResponse).toList(),
                ambiente.lancamentosRecorrentes().stream().map(this::paraResponse).toList(),
                ambiente.movimentacoes().stream().map(this::paraResponse).toList(),
                ambiente.orcamentosMetas().stream().map(this::paraResponse).toList());
    }

    private ContaBancariaResponse paraResponse(ContaBancariaResumo resumo) {
        return new ContaBancariaResponse(resumo.id(), resumo.nome(), resumo.tipo(), resumo.saldoInicial(),
                resumo.saldoAtual(), resumo.moeda(), resumo.ativa());
    }

    private CategoriaResponse paraResponse(CategoriaResumo resumo) {
        return new CategoriaResponse(resumo.id(), resumo.nome(), resumo.tipo(), resumo.categoriaPaiId(), resumo.ativa(),
                List.of());
    }

    private ContatoResponse paraResponse(ContatoResumo resumo) {
        return new ContatoResponse(resumo.id(), resumo.nome(), resumo.tipo(), resumo.documento(), resumo.email(),
                resumo.telefone(), resumo.ativo());
    }

    private LancamentoResponse paraResponse(LancamentoResumo resumo) {
        return new LancamentoResponse(resumo.id(), resumo.tipo(), resumo.status(), resumo.valor(), resumo.dataEmissao(),
                resumo.dataVencimento(), resumo.dataPagamento(), resumo.categoriaId(), resumo.contatoId(),
                resumo.contaBancariaId(), resumo.formaPagamento(), resumo.origem(), resumo.descricao(),
                resumo.observacao(), resumo.linhaDigitavel());
    }

    private LancamentoRecorrenteResponse paraResponse(LancamentoRecorrenteResumo resumo) {
        return new LancamentoRecorrenteResponse(resumo.id(), resumo.descricao(), resumo.tipo(), resumo.valor(),
                resumo.categoriaId(), resumo.contaBancariaId(), resumo.frequencia(), resumo.diaReferencia(),
                resumo.dataInicio(), resumo.dataFim(), resumo.ativo());
    }

    private MovimentacaoResponse paraResponse(MovimentacaoResumo resumo) {
        return new MovimentacaoResponse(resumo.id(), resumo.contaBancariaId(), resumo.tipo(), resumo.valor(),
                resumo.data(), resumo.descricao(), resumo.origem(), resumo.categoriaId(), resumo.lancamentoId(),
                resumo.grupoTransferenciaId(), resumo.criadoEm());
    }

    private OrcamentoMetaResponse paraResponse(OrcamentoMetaResumo resumo) {
        return new OrcamentoMetaResponse(resumo.id(), resumo.tipo(), resumo.categoriaId(), resumo.nome(),
                resumo.valorLimite(), resumo.valorAlvo(), resumo.periodo(), resumo.dataInicio(), resumo.dataFim(),
                resumo.contaBancariaId(), resumo.status(), resumo.valorAtual(), resumo.percentualAtingido(),
                resumo.limiarAlertaPercentual());
    }
}
