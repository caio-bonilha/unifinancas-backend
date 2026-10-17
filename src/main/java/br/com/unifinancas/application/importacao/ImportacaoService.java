package br.com.unifinancas.application.importacao;

import br.com.unifinancas.application.lancamento.LancamentoResumo;
import br.com.unifinancas.application.lancamento.LancamentoService;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.infrastructure.importacao.CsvParser;
import br.com.unifinancas.infrastructure.importacao.ExcelParser;
import br.com.unifinancas.infrastructure.importacao.ExtratoOfx;
import br.com.unifinancas.infrastructure.importacao.ImportacaoParseException;
import br.com.unifinancas.infrastructure.importacao.OfxParseException;
import br.com.unifinancas.infrastructure.importacao.OfxParser;
import br.com.unifinancas.infrastructure.importacao.TabelaImportada;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-21, RNF-02, T4.1.3/T4.1.4: le extratos importados (OFX/CSV/Excel -
 * parsing em si e {@code OfxParser}/{@code CsvParser}/{@code ExcelParser},
 * infrastructure/importacao, chamados diretamente daqui como utilitarios
 * estaticos sem estado - nao ha "provedor" alternativo pra justificar uma
 * Port/Adapter, diferente do OCR/IA, ADR-016/ADR-007), detecta duplicidade
 * e sugere categoria, e confirma a importacao (cria os Lancamentos).
 *
 * <p><b>Fluxo sem estado no backend</b> (decisao consultada com o usuario,
 * T4.1.4): diferente do processamento de Documento (RF-20, assincrono por
 * causa da latencia de OCR/IA), aqui nao ha processamento demorado - nao
 * existe uma tabela {@code importacao} nem um rascunho persistido. O
 * frontend guarda o estado entre as chamadas:
 * <ol>
 *   <li>OFX: {@link #analisarOfx} faz parsing + analise num so passo (nao
 *   ha coluna pra mapear - o formato ja e autodescritivo).</li>
 *   <li>CSV/Excel: {@link #lerTabela} so faz parsing, devolve a tabela
 *   generica pro usuario mapear as colunas na tela de revisao (T4.1.2); o
 *   frontend aplica o mapeamento e chama {@link #analisar} com a lista ja
 *   normalizada.</li>
 *   <li>{@link #confirmar}: o usuario decide quais transacoes analisadas
 *   realmente viram Lancamento (pode editar categoria, remover duplicatas) -
 *   o frontend reenvia so essa lista final.</li>
 * </ol>
 *
 * <p>Duas decisoes de negocio consultadas com o usuario em T4.1.3 (RF-21 nao
 * detalhava nenhuma das duas):
 * <ul>
 *   <li><b>Duplicidade entre importacoes separadas</b> (ex.: reimportar o
 *   mesmo arquivo OFX): {@code Lancamento} ganhou {@code identificadorExterno}
 *   (V35) - o FITID do OFX quando existir, senao um hash deterministico de
 *   contaBancariaId+data+valor+descricao normalizada.</li>
 *   <li><b>Sugestao de categoria</b>: match exato de descricao normalizada
 *   contra a categoria mais usada em Lancamentos anteriores com a mesma
 *   descricao normalizada, no mesmo Ambiente Financeiro.</li>
 * </ul>
 * E uma terceira em T4.1.4: um Lancamento de importacao nasce JA PAGO/
 * RECEBIDO (nunca PENDENTE) - ver {@code LancamentoFinanceiro.novoDeImportacao}.
 */
@Service
public class ImportacaoService {

    private final LancamentoRepository lancamentoRepository;
    private final ContaBancariaRepository contaBancariaRepository;
    private final LancamentoService lancamentoService;

    public ImportacaoService(LancamentoRepository lancamentoRepository, ContaBancariaRepository contaBancariaRepository,
            LancamentoService lancamentoService) {
        this.lancamentoRepository = lancamentoRepository;
        this.contaBancariaRepository = contaBancariaRepository;
        this.lancamentoService = lancamentoService;
    }

    /**
     * T4.1.2: so leitura generica (cabecalho + linhas) - o mapeamento de
     * colunas fica com quem chama (tela de revisao). {@code CsvParser}/
     * {@code ExcelParser} sao infra (nao devem vazar pra presentation) -
     * {@code OfxParseException}/{@code ImportacaoParseException} viram
     * {@code RegraDeNegocioException} (422), mesmo criterio de qualquer
     * outra falha de validacao causada pelo que o usuario enviou.
     */
    public TabelaImportada lerTabela(TenantContext contexto, UUID contaBancariaId, TipoArquivoPlanilha tipoArquivo, byte[] conteudo) {
        validarContaBancaria(contexto, contaBancariaId);
        try {
            return switch (tipoArquivo) {
                case CSV -> CsvParser.parse(conteudo);
                case EXCEL -> ExcelParser.parse(conteudo);
            };
        } catch (ImportacaoParseException e) {
            throw new RegraDeNegocioException("ARQUIVO_INVALIDO", e.getMessage());
        }
    }

    /** T4.1.1/T4.1.3: OFX ja e autodescritivo (sem coluna pra mapear) - parsing e analise num so passo. */
    public List<TransacaoAnalisada> analisarOfx(TenantContext contexto, UUID contaBancariaId, byte[] conteudo) {
        validarContaBancaria(contexto, contaBancariaId);
        ExtratoOfx extrato;
        try {
            extrato = OfxParser.parse(conteudo);
        } catch (OfxParseException e) {
            throw new RegraDeNegocioException("ARQUIVO_INVALIDO", e.getMessage());
        }
        List<TransacaoImportada> transacoes = extrato.transacoes().stream()
                .map(t -> new TransacaoImportada(t.data(), t.valor(), t.descricao(), t.identificadorExterno()))
                .toList();
        return analisarTransacoes(contexto, contaBancariaId, transacoes);
    }

    /** SAD Sec8.2: so analisa (nada e persistido) - leitura, permitida a qualquer perfil. */
    public List<TransacaoAnalisada> analisar(TenantContext contexto, UUID contaBancariaId, List<TransacaoImportada> transacoes) {
        validarContaBancaria(contexto, contaBancariaId);
        return analisarTransacoes(contexto, contaBancariaId, transacoes);
    }

    private List<TransacaoAnalisada> analisarTransacoes(TenantContext contexto, UUID contaBancariaId, List<TransacaoImportada> transacoes) {
        return transacoes.stream().map(transacao -> analisarUma(contexto, contaBancariaId, transacao)).toList();
    }

    private TransacaoAnalisada analisarUma(TenantContext contexto, UUID contaBancariaId, TransacaoImportada transacao) {
        String identificador = resolverIdentificadorExterno(contaBancariaId, transacao);
        Optional<LancamentoFinanceiro> existente = lancamentoRepository
                .findByAmbienteFinanceiroIdAndContaBancariaIdAndIdentificadorExterno(
                        contexto.ambienteFinanceiroId(), contaBancariaId, identificador);

        UUID categoriaSugerida = existente.isPresent() ? null : sugerirCategoria(contexto, transacao.descricao());
        return new TransacaoAnalisada(transacao, identificador, existente.isPresent(),
                existente.map(LancamentoFinanceiro::getId).orElse(null), categoriaSugerida);
    }

    /**
     * T4.1.4, RF-21 (decisao consultada com o usuario): cria um Lancamento
     * PAGO/RECEBIDO pra cada transacao confirmada - reverifica duplicidade
     * (defesa contra reenvio duplo do mesmo lote de confirmacao; a
     * verificacao original foi em {@link #analisar}, antes da revisao do
     * usuario) e simplesmente pula quem ja foi importado, sem erro -
     * idempotente, RNF-02.
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public List<LancamentoResumo> confirmar(TenantContext contexto, UUID contaBancariaId, List<TransacaoConfirmada> transacoes) {
        validarContaBancaria(contexto, contaBancariaId);
        List<LancamentoResumo> criados = new ArrayList<>();
        for (TransacaoConfirmada transacao : transacoes) {
            boolean jaImportada = lancamentoRepository
                    .findByAmbienteFinanceiroIdAndContaBancariaIdAndIdentificadorExterno(
                            contexto.ambienteFinanceiroId(), contaBancariaId, transacao.identificadorExterno())
                    .isPresent();
            if (jaImportada) {
                continue;
            }
            TipoLancamento tipo = transacao.valor().signum() < 0 ? TipoLancamento.DESPESA : TipoLancamento.RECEITA;
            criados.add(lancamentoService.criarDeImportacao(contexto, tipo, transacao.valor().abs(), transacao.data(),
                    transacao.categoriaId(), contaBancariaId, transacao.descricao(), transacao.identificadorExterno()));
        }
        return criados;
    }

    /** FITID do OFX quando existir; senao um hash sintetico e deterministico (mesma transacao reimportada gera o mesmo identificador). */
    private String resolverIdentificadorExterno(UUID contaBancariaId, TransacaoImportada transacao) {
        if (transacao.identificadorExterno() != null && !transacao.identificadorExterno().isBlank()) {
            return transacao.identificadorExterno();
        }
        return hashTransacao(contaBancariaId, transacao.data(), transacao.valor(), transacao.descricao());
    }

    private static String hashTransacao(UUID contaBancariaId, java.time.LocalDate data, BigDecimal valor, String descricao) {
        String chave = contaBancariaId + "|" + data + "|" + valor.stripTrailingZeros().toPlainString() + "|" + normalizar(descricao);
        try {
            MessageDigest digestor = MessageDigest.getInstance("SHA-256");
            byte[] hash = digestor.digest(chave.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nao disponivel na JVM.", e);
        }
    }

    private UUID sugerirCategoria(TenantContext contexto, String descricao) {
        List<UUID> categorias = lancamentoRepository
                .categoriasPorDescricaoNormalizadaOrdenadasPorFrequencia(contexto.ambienteFinanceiroId(), normalizar(descricao));
        return categorias.isEmpty() ? null : categorias.get(0);
    }

    private static String normalizar(String descricao) {
        return descricao == null ? "" : descricao.trim().toLowerCase(Locale.ROOT);
    }

    private void validarContaBancaria(TenantContext contexto, UUID contaBancariaId) {
        contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CONTA_BANCARIA_NAO_ENCONTRADA", "Conta Bancaria nao encontrada neste Ambiente Financeiro."));
    }
}
