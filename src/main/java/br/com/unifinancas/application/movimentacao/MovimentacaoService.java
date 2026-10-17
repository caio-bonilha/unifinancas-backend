package br.com.unifinancas.application.movimentacao;

import br.com.unifinancas.application.contabancaria.ContaBancariaService;
import br.com.unifinancas.application.lancamento.LancamentoService;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.categoria.CategoriaRepository;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.movimentacao.Movimentacao;
import br.com.unifinancas.domain.movimentacao.MovimentacaoRepository;
import br.com.unifinancas.domain.movimentacao.OrigemMovimentacao;
import br.com.unifinancas.domain.movimentacao.TipoMovimentacao;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-012, SAD Sec4.4a/Sec7.2: extrato paginado de uma Conta Bancaria e
 * criacao de Transferencia (as duas Movimentacoes atomicas, sem Lancamento -
 * RN-05).
 */
@Service
public class MovimentacaoService {

    private static final int LIMITE_PADRAO = 20;
    private static final int LIMITE_MAXIMO = 100;

    private final MovimentacaoRepository movimentacaoRepository;
    private final ContaBancariaRepository contaBancariaRepository;
    private final ContaBancariaService contaBancariaService;
    private final CategoriaRepository categoriaRepository;
    private final LancamentoService lancamentoService;

    public MovimentacaoService(MovimentacaoRepository movimentacaoRepository,
            ContaBancariaRepository contaBancariaRepository, ContaBancariaService contaBancariaService,
            CategoriaRepository categoriaRepository, LancamentoService lancamentoService) {
        this.movimentacaoRepository = movimentacaoRepository;
        this.contaBancariaRepository = contaBancariaRepository;
        this.contaBancariaService = contaBancariaService;
        this.categoriaRepository = categoriaRepository;
        this.lancamentoService = lancamentoService;
    }

    /**
     * SAD Sec8.2: leitura e permitida a qualquer perfil, inclusive
     * VISUALIZADOR (mesmo criterio de {@code LancamentoService.listar}).
     * Paginacao por cursor, nao offset (Sec7.1/Sec17.4) - o extrato de uma
     * Conta Bancaria cresce sem limite.
     */
    public PaginaMovimentacoes extrato(TenantContext contexto, UUID contaBancariaId, LocalDate dataDe,
            LocalDate dataAte, String cursorBruto, Integer limite) {
        UUID ambienteId = contexto.ambienteFinanceiroId();
        contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CONTA_BANCARIA_NAO_ENCONTRADA", "Conta Bancaria nao encontrada neste Ambiente Financeiro."));

        int tamanhoPagina = validarLimite(limite);
        CursorMovimentacao cursor = cursorBruto != null ? CursorMovimentacao.decodificar(cursorBruto) : null;

        Specification<Movimentacao> especificacao = construirEspecificacao(ambienteId, contaBancariaId, dataDe, dataAte, cursor);
        Sort ordenacao = Sort.by(Sort.Order.asc("data"), Sort.Order.asc("id"));

        List<Movimentacao> encontradas = movimentacaoRepository
                .findAll(especificacao, PageRequest.of(0, tamanhoPagina + 1, ordenacao))
                .getContent();

        boolean temProximaPagina = encontradas.size() > tamanhoPagina;
        List<Movimentacao> pagina = temProximaPagina ? encontradas.subList(0, tamanhoPagina) : encontradas;

        String proximoCursor = null;
        if (temProximaPagina) {
            Movimentacao ultima = pagina.get(pagina.size() - 1);
            proximoCursor = new CursorMovimentacao(ultima.getData(), ultima.getId()).codificar();
        }

        return new PaginaMovimentacoes(pagina.stream().map(this::paraResumo).toList(), proximoCursor);
    }

    /**
     * ADR-012, RN-05: cria uma Transferencia - duas Movimentacoes (DEBITO na
     * origem, CREDITO no destino) compartilhando um {@code grupoTransferenciaId},
     * na mesma transacao, e recalcula o saldo (RN-12) das duas contas.
     * Instantanea - sem estado PENDENTE (uma transferencia bancaria real nao
     * fica "aguardando confirmacao").
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public TransferenciaResumo criarTransferencia(TenantContext contexto, UUID contaBancariaOrigemId,
            UUID contaBancariaDestinoId, BigDecimal valor, LocalDate data, String descricao) {
        UUID ambienteId = contexto.ambienteFinanceiroId();
        validarTransferencia(ambienteId, contaBancariaOrigemId, contaBancariaDestinoId);

        UUID grupoTransferenciaId = movimentacaoRepository.gerarUuidV7();
        Movimentacao debito = Movimentacao.paraTransferencia(ambienteId, contaBancariaOrigemId, TipoMovimentacao.DEBITO,
                valor, data, descricao, grupoTransferenciaId);
        Movimentacao credito = Movimentacao.paraTransferencia(ambienteId, contaBancariaDestinoId, TipoMovimentacao.CREDITO,
                valor, data, descricao, grupoTransferenciaId);
        movimentacaoRepository.save(debito);
        movimentacaoRepository.save(credito);

        contaBancariaService.recalcularSaldo(ambienteId, contaBancariaOrigemId);
        contaBancariaService.recalcularSaldo(ambienteId, contaBancariaDestinoId);

        return new TransferenciaResumo(paraResumo(debito), paraResumo(credito));
    }

    /** ADR-014: cria uma Movimentacao avulsa (origem=MANUAL) - conta e, se informada, categoria precisam existir no ambiente. */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public MovimentacaoResumo criar(TenantContext contexto, UUID contaBancariaId, TipoMovimentacao tipo,
            BigDecimal valor, LocalDate data, String descricao, UUID categoriaId) {
        UUID ambienteId = contexto.ambienteFinanceiroId();
        validarContaBancaria(ambienteId, contaBancariaId);
        validarCategoria(ambienteId, categoriaId);

        Movimentacao movimentacao = Movimentacao.paraAvulsa(ambienteId, contaBancariaId, tipo, valor, data, descricao, categoriaId);
        movimentacaoRepository.save(movimentacao);
        contaBancariaService.recalcularSaldo(ambienteId, contaBancariaId);
        return paraResumo(movimentacao);
    }

    /** ADR-014: so aceita Movimentacao avulsa (origem=MANUAL) - {@code Movimentacao.editar} lanca MOVIMENTACAO_NAO_EDITAVEL nas demais. */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public MovimentacaoResumo editar(TenantContext contexto, UUID movimentacaoId, TipoMovimentacao tipo,
            BigDecimal valor, LocalDate data, String descricao, UUID categoriaId) {
        UUID ambienteId = contexto.ambienteFinanceiroId();
        Movimentacao movimentacao = localizar(ambienteId, movimentacaoId);
        validarCategoria(ambienteId, categoriaId);

        movimentacao.editar(tipo, valor, data, descricao, categoriaId);
        movimentacaoRepository.save(movimentacao);
        contaBancariaService.recalcularSaldo(ambienteId, movimentacao.getContaBancariaId());
        return paraResumo(movimentacao);
    }

    /**
     * ADR-014: exclusao unificada, comportamento varia por origem -
     * `MANUAL`: apaga e recalcula o saldo; `LIQUIDACAO`: delega para
     * {@code LancamentoService.reabrir} (que ja apaga a Movimentacao,
     * reabre o Lancamento e recalcula o saldo); `TRANSFERENCIA`: apaga as
     * duas pernas do mesmo grupoTransferenciaId e recalcula o saldo das
     * duas contas - excluir um lado e entendido como a transferencia sendo
     * devolvida (decisao consultada com o usuario).
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public void excluir(TenantContext contexto, UUID movimentacaoId) {
        UUID ambienteId = contexto.ambienteFinanceiroId();
        Movimentacao movimentacao = localizar(ambienteId, movimentacaoId);

        switch (movimentacao.getOrigem()) {
            case MANUAL -> {
                UUID contaBancariaId = movimentacao.getContaBancariaId();
                movimentacaoRepository.delete(movimentacao);
                contaBancariaService.recalcularSaldo(ambienteId, contaBancariaId);
            }
            case LIQUIDACAO -> lancamentoService.reabrir(contexto, movimentacao.getLancamentoId());
            case TRANSFERENCIA -> excluirParDeTransferencia(ambienteId, movimentacao);
        }
    }

    /** Apaga as duas pernas do grupoTransferenciaId e recalcula o saldo das duas contas envolvidas. */
    private void excluirParDeTransferencia(UUID ambienteId, Movimentacao movimentacao) {
        List<Movimentacao> par = movimentacaoRepository
                .findAllByAmbienteFinanceiroIdAndGrupoTransferenciaId(ambienteId, movimentacao.getGrupoTransferenciaId());
        List<UUID> contasEnvolvidas = par.stream().map(Movimentacao::getContaBancariaId).distinct().toList();
        movimentacaoRepository.deleteAll(par);
        contasEnvolvidas.forEach(contaId -> contaBancariaService.recalcularSaldo(ambienteId, contaId));
    }

    private Movimentacao localizar(UUID ambienteId, UUID movimentacaoId) {
        return movimentacaoRepository.findByIdAndAmbienteFinanceiroId(movimentacaoId, ambienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "MOVIMENTACAO_NAO_ENCONTRADA", "Movimentacao nao encontrada neste Ambiente Financeiro."));
    }

    private void validarContaBancaria(UUID ambienteId, UUID contaBancariaId) {
        contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CONTA_BANCARIA_NAO_ENCONTRADA", "Conta Bancaria nao encontrada neste Ambiente Financeiro."));
    }

    /** SAD Sec8.7/Sec12.4: nunca aceitar id de Categoria de outro tenant. categoriaId e opcional (ADR-014). */
    private void validarCategoria(UUID ambienteId, UUID categoriaId) {
        if (categoriaId == null) {
            return;
        }
        categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CATEGORIA_NAO_ENCONTRADA", "Categoria nao encontrada neste Ambiente Financeiro."));
    }

    /** RN-05: origem e destino devem existir no ambiente e ser contas distintas. */
    private void validarTransferencia(UUID ambienteId, UUID contaBancariaOrigemId, UUID contaBancariaDestinoId) {
        if (contaBancariaOrigemId.equals(contaBancariaDestinoId)) {
            throw new RegraDeNegocioException("TRANSFERENCIA_MESMA_CONTA", "Origem e destino nao podem ser a mesma conta.");
        }
        contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaOrigemId, ambienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CONTA_BANCARIA_NAO_ENCONTRADA", "Conta Bancaria nao encontrada neste Ambiente Financeiro."));
        contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaDestinoId, ambienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CONTA_BANCARIA_NAO_ENCONTRADA", "Conta Bancaria nao encontrada neste Ambiente Financeiro."));
    }

    private int validarLimite(Integer limite) {
        int valor = limite != null ? limite : LIMITE_PADRAO;
        if (valor < 1 || valor > LIMITE_MAXIMO) {
            throw new RegraDeNegocioException("LIMITE_INVALIDO", "limit deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return valor;
    }

    private Specification<Movimentacao> construirEspecificacao(UUID ambienteFinanceiroId, UUID contaBancariaId,
            LocalDate dataDe, LocalDate dataAte, CursorMovimentacao cursor) {
        Specification<Movimentacao> especificacao = (root, query, cb) -> cb.and(
                cb.equal(root.get("ambienteFinanceiroId"), ambienteFinanceiroId),
                cb.equal(root.get("contaBancariaId"), contaBancariaId));

        if (dataDe != null) {
            especificacao = especificacao.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("data"), dataDe));
        }
        if (dataAte != null) {
            especificacao = especificacao.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("data"), dataAte));
        }
        if (cursor != null) {
            especificacao = especificacao.and((root, query, cb) -> cb.or(
                    cb.greaterThan(root.get("data"), cursor.data()),
                    cb.and(cb.equal(root.get("data"), cursor.data()), cb.greaterThan(root.get("id"), cursor.id()))));
        }
        return especificacao;
    }

    private MovimentacaoResumo paraResumo(Movimentacao movimentacao) {
        return new MovimentacaoResumo(movimentacao.getId(), movimentacao.getContaBancariaId(), movimentacao.getTipo(),
                movimentacao.getValor(), movimentacao.getData(), movimentacao.getDescricao(), movimentacao.getOrigem(),
                movimentacao.getCategoriaId(), movimentacao.getLancamentoId(), movimentacao.getGrupoTransferenciaId(),
                movimentacao.getCriadoEm());
    }
}
