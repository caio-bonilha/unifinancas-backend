package br.com.unifinancas.application.lancamento;

import br.com.unifinancas.application.contabancaria.ContaBancariaService;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.categoria.CategoriaRepository;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.contato.ContatoRepository;
import br.com.unifinancas.domain.lancamento.FormaPagamento;
import br.com.unifinancas.domain.lancamento.LancamentoCriadoEvent;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.lancamento.StatusLancamento;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.movimentacao.Movimentacao;
import br.com.unifinancas.domain.movimentacao.MovimentacaoRepository;
import br.com.unifinancas.domain.movimentacao.TipoMovimentacao;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-06/RF-07/RF-08, SAD Sec4.7, Fluxo 6.2: criar/editar/confirmar/cancelar
 * e listar (com filtros e paginacao por cursor) Lancamento Financeiro.
 */
@Service
public class LancamentoService {

    private static final int LIMITE_PADRAO = 20;
    private static final int LIMITE_MAXIMO = 100;

    private final LancamentoRepository lancamentoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContatoRepository contatoRepository;
    private final ContaBancariaRepository contaBancariaRepository;
    private final ContaBancariaService contaBancariaService;
    private final MovimentacaoRepository movimentacaoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public LancamentoService(LancamentoRepository lancamentoRepository, CategoriaRepository categoriaRepository,
            ContatoRepository contatoRepository, ContaBancariaRepository contaBancariaRepository,
            ContaBancariaService contaBancariaService, MovimentacaoRepository movimentacaoRepository,
            ApplicationEventPublisher eventPublisher) {
        this.contaBancariaService = contaBancariaService;
        this.lancamentoRepository = lancamentoRepository;
        this.categoriaRepository = categoriaRepository;
        this.contatoRepository = contatoRepository;
        this.contaBancariaRepository = contaBancariaRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * SAD Sec8.2: "Criar/editar Lancamento" exige OWNER, ADMIN ou MEMBRO -
     * VISUALIZADOR nao pode. Publica {@code LancamentoCriadoEvent} (T1.3.4,
     * Sec3.4) apos persistir - so na criacao (Fluxo 6.2), nao em
     * editar/confirmar/cancelar.
     */
    /** Sobrecarga sem {@code linhaDigitavel} (Etapa A) - mantida para callers que não têm boleto. */
    public LancamentoResumo criar(TenantContext contexto, TipoLancamento tipo, BigDecimal valor,
            LocalDate dataEmissao, LocalDate dataVencimento, UUID categoriaId, UUID contatoId, UUID contaBancariaId,
            FormaPagamento formaPagamento, String descricao, String observacao) {
        return criar(contexto, tipo, valor, dataEmissao, dataVencimento, categoriaId, contatoId, contaBancariaId,
                formaPagamento, descricao, observacao, null);
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public LancamentoResumo criar(TenantContext contexto, TipoLancamento tipo, BigDecimal valor,
            LocalDate dataEmissao, LocalDate dataVencimento, UUID categoriaId, UUID contatoId, UUID contaBancariaId,
            FormaPagamento formaPagamento, String descricao, String observacao, String linhaDigitavel) {
        validarReferenciasDoAmbiente(contexto, categoriaId, contatoId, contaBancariaId);

        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novo(contexto.ambienteFinanceiroId(), tipo, valor,
                dataEmissao, dataVencimento, categoriaId, contatoId, contaBancariaId,
                formaPagamento, descricao, observacao);
        lancamento.definirLinhaDigitavel(linhaDigitavel);
        lancamentoRepository.save(lancamento);
        eventPublisher.publishEvent(LancamentoCriadoEvent.de(lancamento, contexto.usuarioId()));
        return paraResumo(lancamento);
    }

    /**
     * T1.6.3, Fluxo 6.6: cria um Lancamento de origem RECORRENCIA a partir
     * do RecorrenciaJob - mesmo caminho de evento de dominio do fluxo 6.2
     * (OrcamentoMetaService/AuditoriaService reagem igual, T1.5.3/T1.3.7).
     * Sem {@link TenantContext}/{@code @RequerPerfil}: quem chama e um job
     * assincrono, nao um usuario autenticado (Sec12.6, mesmo criterio de
     * {@code VencimentoLancamentoJobHandler}, T1.5.4) - as referencias
     * (categoriaId/contaBancariaId) ja foram validadas quando a propria
     * recorrencia foi criada (T1.6.2), nao precisam ser revalidadas aqui.
     */
    @Transactional
    public LancamentoResumo criarDeRecorrencia(UUID ambienteFinanceiroId, UUID lancamentoRecorrenteId,
            TipoLancamento tipo, BigDecimal valor, LocalDate dataEmissao, LocalDate dataVencimento, UUID categoriaId,
            UUID contaBancariaId, String descricao, String observacao) {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novoDeRecorrencia(ambienteFinanceiroId,
                lancamentoRecorrenteId, tipo, valor, dataEmissao, dataVencimento, categoriaId, contaBancariaId,
                descricao, observacao);
        lancamentoRepository.save(lancamento);
        eventPublisher.publishEvent(LancamentoCriadoEvent.de(lancamento, null));
        return paraResumo(lancamento);
    }

    /**
     * T3.1.5, SAD Fluxo 6.3 (RN-08): cria um Lancamento de origem DOCUMENTO a
     * partir dos dados revisados e confirmados pelo usuario sobre um documento
     * processado. Mesmo caminho de {@link #criar} (validacao de referencias do
     * Ambiente + {@code LancamentoCriadoEvent} para orcamento/auditoria) -
     * so muda a origem. `[Revisado - Etapa B]` O vinculo com o Documento de
     * origem passou para {@code documento.lancamento_id} (N:1) e e gravado
     * pelo {@code ProcessamentoDocumentoService} logo apos esta chamada.
     */
    /** Sobrecarga sem {@code linhaDigitavel} (Etapa A). */
    public LancamentoResumo criarDeDocumento(TenantContext contexto, TipoLancamento tipo, BigDecimal valor,
            LocalDate dataEmissao, LocalDate dataVencimento, UUID categoriaId, UUID contatoId, UUID contaBancariaId,
            FormaPagamento formaPagamento, String descricao, String observacao) {
        return criarDeDocumento(contexto, tipo, valor, dataEmissao, dataVencimento, categoriaId, contatoId,
                contaBancariaId, formaPagamento, descricao, observacao, null);
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public LancamentoResumo criarDeDocumento(TenantContext contexto, TipoLancamento tipo, BigDecimal valor,
            LocalDate dataEmissao, LocalDate dataVencimento, UUID categoriaId, UUID contatoId, UUID contaBancariaId,
            FormaPagamento formaPagamento, String descricao, String observacao, String linhaDigitavel) {
        validarReferenciasDoAmbiente(contexto, categoriaId, contatoId, contaBancariaId);

        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novoDeDocumento(contexto.ambienteFinanceiroId(), tipo,
                valor, dataEmissao, dataVencimento, categoriaId, contatoId, contaBancariaId, formaPagamento,
                descricao, observacao);
        lancamento.definirLinhaDigitavel(linhaDigitavel);
        lancamentoRepository.save(lancamento);
        eventPublisher.publishEvent(LancamentoCriadoEvent.de(lancamento, contexto.usuarioId()));
        return paraResumo(lancamento);
    }

    /**
     * T4.1.4, RF-21 (decisao consultada com o usuario): cria um Lancamento
     * de origem IMPORTACAO ja PAGO/RECEBIDO - diferente de {@link #criar}/
     * {@link #criarDeDocumento} (sempre PENDENTE), uma linha de extrato
     * bancario representa algo que ja aconteceu. Por isso, alem do evento
     * de dominio (orcamento/auditoria, mesmo caminho de {@link #criar}),
     * cria a Movimentacao e recalcula o saldo na mesma transacao - mesmo
     * criterio de {@link #confirmarPagamento}/{@link #confirmarRecebimento}
     * (RN-12), so que aqui acontece na propria criacao, nao numa transicao
     * separada. {@code ImportacaoService} decide o resto (deteccao de
     * duplicidade antes de chamar este metodo).
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public LancamentoResumo criarDeImportacao(TenantContext contexto, TipoLancamento tipo, BigDecimal valor,
            LocalDate data, UUID categoriaId, UUID contaBancariaId, String descricao, String identificadorExterno) {
        validarReferenciasDoAmbiente(contexto, categoriaId, null, contaBancariaId);

        LancamentoFinanceiro lancamento = LancamentoFinanceiro.novoDeImportacao(contexto.ambienteFinanceiroId(), tipo,
                valor, data, categoriaId, contaBancariaId, descricao, identificadorExterno);
        lancamentoRepository.save(lancamento);
        eventPublisher.publishEvent(LancamentoCriadoEvent.de(lancamento, contexto.usuarioId()));

        TipoMovimentacao tipoMovimentacao = tipo == TipoLancamento.DESPESA ? TipoMovimentacao.DEBITO : TipoMovimentacao.CREDITO;
        criarMovimentacaoDaLiquidacao(contexto, lancamento, tipoMovimentacao, data);
        return paraResumo(lancamento);
    }

    /** Sobrecarga sem {@code linhaDigitavel} (Etapa A). */
    public LancamentoResumo editar(TenantContext contexto, UUID lancamentoId, BigDecimal valor, LocalDate dataEmissao,
            LocalDate dataVencimento, UUID categoriaId, UUID contatoId, UUID contaBancariaId,
            FormaPagamento formaPagamento, String descricao, String observacao) {
        return editar(contexto, lancamentoId, valor, dataEmissao, dataVencimento, categoriaId, contatoId,
                contaBancariaId, formaPagamento, descricao, observacao, null);
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public LancamentoResumo editar(TenantContext contexto, UUID lancamentoId, BigDecimal valor, LocalDate dataEmissao,
            LocalDate dataVencimento, UUID categoriaId, UUID contatoId, UUID contaBancariaId,
            FormaPagamento formaPagamento, String descricao, String observacao, String linhaDigitavel) {
        LancamentoFinanceiro lancamento = localizar(contexto, lancamentoId);
        validarReferenciasDoAmbiente(contexto, categoriaId, contatoId, contaBancariaId);

        lancamento.editar(valor, dataEmissao, dataVencimento, categoriaId, contatoId, contaBancariaId,
                formaPagamento, descricao, observacao);
        lancamento.definirLinhaDigitavel(linhaDigitavel);
        lancamentoRepository.save(lancamento);
        return paraResumo(lancamento);
    }

    /**
     * [Revisado - ADR-012, RN-12] Sec6.5: confirmacao de pagamento, so para
     * DESPESA - cria a Movimentacao DEBITO vinculada a este Lancamento e
     * recalcula o saldo derivado (RN-12) da Conta Bancaria afetada, na
     * mesma transacao - consistencia forte, nao via evento assincrono
     * (Sec13.5 so adia efeitos com sistemas externos). `[Revisado - ADR-015]`
     * {@code contaBancariaId}/{@code formaPagamento} sao resolvidos por
     * campo dentro de {@code LancamentoFinanceiro.confirmarPagamento} -
     * exigidos so quando o Lancamento ainda nao os tem.
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public LancamentoResumo confirmarPagamento(TenantContext contexto, UUID lancamentoId, LocalDate dataPagamento,
            UUID contaBancariaId, FormaPagamento formaPagamento) {
        LancamentoFinanceiro lancamento = localizar(contexto, lancamentoId);
        lancamento.confirmarPagamento(dataPagamento, contaBancariaId, formaPagamento);
        lancamentoRepository.save(lancamento);
        criarMovimentacaoDaLiquidacao(contexto, lancamento, TipoMovimentacao.DEBITO, dataPagamento);
        return paraResumo(lancamento);
    }

    /** [Revisado - ADR-012, RN-12, ADR-015] Sec6.5: confirmacao de recebimento - so RECEITA. Mesma logica de {@link #confirmarPagamento}, com Movimentacao CREDITO. */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public LancamentoResumo confirmarRecebimento(TenantContext contexto, UUID lancamentoId, LocalDate dataPagamento,
            UUID contaBancariaId, FormaPagamento formaPagamento) {
        LancamentoFinanceiro lancamento = localizar(contexto, lancamentoId);
        lancamento.confirmarRecebimento(dataPagamento, contaBancariaId, formaPagamento);
        lancamentoRepository.save(lancamento);
        criarMovimentacaoDaLiquidacao(contexto, lancamento, TipoMovimentacao.CREDITO, dataPagamento);
        return paraResumo(lancamento);
    }

    /**
     * ADR-015, T1.3c.4: confirma pagamento/recebimento de varios Lancamentos
     * de uma vez - todos precisam ser do mesmo {@code tipo} (LOTE_COM_TIPOS_MISTOS,
     * decisao consultada: contas a pagar/receber ja sao visoes separadas na
     * UI, RN-06). {@code contaBancariaId}/{@code formaPagamento} valem como
     * fallback - cada Lancamento resolve por campo (mesma regra de {@link
     * LancamentoFinanceiro#confirmarPagamento}), entao um item que ja tinha
     * os dois preenchidos nao e afetado pelo fallback. Atomico - qualquer
     * falha (Lancamento de outro Ambiente, campo obrigatorio faltando,
     * status incompativel) desfaz o lote inteiro.
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public List<LancamentoResumo> quitarEmLote(TenantContext contexto, List<UUID> lancamentoIds,
            LocalDate dataPagamento, UUID contaBancariaId, FormaPagamento formaPagamento) {
        UUID ambienteId = contexto.ambienteFinanceiroId();
        List<LancamentoFinanceiro> lancamentos = lancamentoRepository.findAllByIdInAndAmbienteFinanceiroId(lancamentoIds, ambienteId);
        if (lancamentos.size() != lancamentoIds.size()) {
            throw new RecursoNaoEncontradoException(
                    "LANCAMENTO_NAO_ENCONTRADO", "Um ou mais Lancamentos nao foram encontrados neste Ambiente Financeiro.");
        }

        TipoLancamento tipoDoLote = lancamentos.get(0).getTipo();
        boolean tiposMistos = lancamentos.stream().anyMatch(lancamento -> lancamento.getTipo() != tipoDoLote);
        if (tiposMistos) {
            throw new RegraDeNegocioException(
                    "LOTE_COM_TIPOS_MISTOS", "Todos os Lancamentos do lote precisam ser do mesmo tipo.");
        }

        TipoMovimentacao tipoMovimentacao = tipoDoLote == TipoLancamento.DESPESA
                ? TipoMovimentacao.DEBITO : TipoMovimentacao.CREDITO;
        for (LancamentoFinanceiro lancamento : lancamentos) {
            if (tipoDoLote == TipoLancamento.DESPESA) {
                lancamento.confirmarPagamento(dataPagamento, contaBancariaId, formaPagamento);
            } else {
                lancamento.confirmarRecebimento(dataPagamento, contaBancariaId, formaPagamento);
            }
            lancamentoRepository.save(lancamento);
            criarMovimentacaoDaLiquidacao(contexto, lancamento, tipoMovimentacao, dataPagamento);
        }
        return lancamentos.stream().map(this::paraResumo).toList();
    }

    /** ADR-015: desde que a quitacao exige Conta Bancaria, o Lancamento sempre tem uma ao chegar aqui - sem guarda de nulo. */
    private void criarMovimentacaoDaLiquidacao(TenantContext contexto, LancamentoFinanceiro lancamento,
            TipoMovimentacao tipoMovimentacao, LocalDate dataPagamento) {
        Movimentacao movimentacao = Movimentacao.paraLancamento(contexto.ambienteFinanceiroId(),
                lancamento.getContaBancariaId(), tipoMovimentacao, lancamento.getValor(), dataPagamento,
                lancamento.getDescricao(), lancamento.getId());
        movimentacaoRepository.save(movimentacao);
        contaBancariaService.recalcularSaldo(contexto.ambienteFinanceiroId(), lancamento.getContaBancariaId());
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public LancamentoResumo cancelar(TenantContext contexto, UUID lancamentoId) {
        LancamentoFinanceiro lancamento = localizar(contexto, lancamentoId);
        lancamento.cancelar();
        lancamentoRepository.save(lancamento);
        return paraResumo(lancamento);
    }

    /**
     * [Gap identificado pelo usuario, decisao consultada]: desfaz confirmar
     * pagamento/recebimento. [Revisado - ADR-012, RN-12]: remove a
     * Movimentacao vinculada a este Lancamento (se existir - Lancamentos
     * sem Conta Bancaria nunca tiveram uma) antes de recalcular o saldo, ja
     * que {@code recalcularSaldo} soma as Movimentacoes existentes.
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public LancamentoResumo reabrir(TenantContext contexto, UUID lancamentoId) {
        LancamentoFinanceiro lancamento = localizar(contexto, lancamentoId);
        UUID contaBancariaId = lancamento.getContaBancariaId();
        lancamento.reabrir();
        lancamentoRepository.save(lancamento);

        movimentacaoRepository.findByAmbienteFinanceiroIdAndLancamentoId(contexto.ambienteFinanceiroId(), lancamentoId)
                .ifPresent(movimentacaoRepository::delete);
        if (contaBancariaId != null) {
            contaBancariaService.recalcularSaldo(contexto.ambienteFinanceiroId(), contaBancariaId);
        }
        return paraResumo(lancamento);
    }

    /**
     * SAD Sec7.2 (RF-06/07/08): leitura e permitida a qualquer perfil,
     * inclusive VISUALIZADOR. {@code tipos}/{@code status} vazios ou nulos
     * nao filtram por aquele campo; contas a pagar/receber (RN-06) sao so
     * chamadas particulares desta listagem (tipo=DESPESA ou RECEITA,
     * status=PENDENTE,ATRASADO). Paginacao por cursor, nao offset
     * (Sec7.1/Sec17.4) - o historico de Lancamentos cresce sem limite.
     */
    public PaginaLancamentos listar(TenantContext contexto, List<TipoLancamento> tipos, List<StatusLancamento> status,
            LocalDate dataVencimentoDe, LocalDate dataVencimentoAte, String cursorBruto, Integer limite) {
        int tamanhoPagina = validarLimite(limite);
        CursorLancamento cursor = cursorBruto != null ? CursorLancamento.decodificar(cursorBruto) : null;

        Specification<LancamentoFinanceiro> especificacao = construirEspecificacao(contexto.ambienteFinanceiroId(),
                tipos, status, dataVencimentoDe, dataVencimentoAte, cursor);
        Sort ordenacao = Sort.by(Sort.Order.asc("dataVencimento"), Sort.Order.asc("id"));

        List<LancamentoFinanceiro> encontrados = lancamentoRepository
                .findAll(especificacao, PageRequest.of(0, tamanhoPagina + 1, ordenacao))
                .getContent();

        boolean temProximaPagina = encontrados.size() > tamanhoPagina;
        List<LancamentoFinanceiro> pagina = temProximaPagina ? encontrados.subList(0, tamanhoPagina) : encontrados;

        String proximoCursor = null;
        if (temProximaPagina) {
            LancamentoFinanceiro ultimo = pagina.get(pagina.size() - 1);
            proximoCursor = new CursorLancamento(ultimo.getDataVencimento(), ultimo.getId()).codificar();
        }

        return new PaginaLancamentos(pagina.stream().map(this::paraResumo).toList(), proximoCursor);
    }

    private int validarLimite(Integer limite) {
        int valor = limite != null ? limite : LIMITE_PADRAO;
        if (valor < 1 || valor > LIMITE_MAXIMO) {
            throw new RegraDeNegocioException("LIMITE_INVALIDO", "limit deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return valor;
    }

    private Specification<LancamentoFinanceiro> construirEspecificacao(UUID ambienteFinanceiroId,
            List<TipoLancamento> tipos, List<StatusLancamento> status, LocalDate dataVencimentoDe,
            LocalDate dataVencimentoAte, CursorLancamento cursor) {
        Specification<LancamentoFinanceiro> especificacao =
                (root, query, cb) -> cb.equal(root.get("ambienteFinanceiroId"), ambienteFinanceiroId);

        if (tipos != null && !tipos.isEmpty()) {
            especificacao = especificacao.and((root, query, cb) -> root.get("tipo").in(tipos));
        }
        if (status != null && !status.isEmpty()) {
            especificacao = especificacao.and((root, query, cb) -> root.get("status").in(status));
        }
        if (dataVencimentoDe != null) {
            especificacao = especificacao
                    .and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dataVencimento"), dataVencimentoDe));
        }
        if (dataVencimentoAte != null) {
            especificacao = especificacao
                    .and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("dataVencimento"), dataVencimentoAte));
        }
        if (cursor != null) {
            especificacao = especificacao.and((root, query, cb) -> cb.or(
                    cb.greaterThan(root.get("dataVencimento"), cursor.dataVencimento()),
                    cb.and(cb.equal(root.get("dataVencimento"), cursor.dataVencimento()),
                            cb.greaterThan(root.get("id"), cursor.id()))));
        }
        return especificacao;
    }

    private LancamentoFinanceiro localizar(TenantContext contexto, UUID lancamentoId) {
        return lancamentoRepository.findByIdAndAmbienteFinanceiroId(lancamentoId, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "LANCAMENTO_NAO_ENCONTRADO", "Lancamento nao encontrado neste Ambiente Financeiro."));
    }

    /** SAD Sec8.7/Sec12.4: nunca aceitar id de Categoria/Contato/Conta Bancaria de outro tenant. */
    private void validarReferenciasDoAmbiente(TenantContext contexto, UUID categoriaId, UUID contatoId,
            UUID contaBancariaId) {
        UUID ambienteId = contexto.ambienteFinanceiroId();
        if (categoriaId != null) {
            categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "CATEGORIA_NAO_ENCONTRADA", "Categoria nao encontrada neste Ambiente Financeiro."));
        }
        if (contatoId != null) {
            contatoRepository.findByIdAndAmbienteFinanceiroId(contatoId, ambienteId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "CONTATO_NAO_ENCONTRADO", "Contato nao encontrado neste Ambiente Financeiro."));
        }
        if (contaBancariaId != null) {
            contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "CONTA_BANCARIA_NAO_ENCONTRADA", "Conta Bancaria nao encontrada neste Ambiente Financeiro."));
        }
    }

    private LancamentoResumo paraResumo(LancamentoFinanceiro lancamento) {
        return new LancamentoResumo(lancamento.getId(), lancamento.getTipo(), lancamento.getStatus(),
                lancamento.getValor(), lancamento.getDataEmissao(), lancamento.getDataVencimento(),
                lancamento.getDataPagamento(), lancamento.getCategoriaId(), lancamento.getContatoId(),
                lancamento.getContaBancariaId(),
                lancamento.getFormaPagamento(), lancamento.getOrigem(), lancamento.getDescricao(),
                lancamento.getObservacao(), lancamento.getLinhaDigitavel());
    }
}
