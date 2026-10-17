package br.com.unifinancas.application.fatura;

import br.com.unifinancas.application.shared.DivisorDeParcelas;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.cartao.Cartao;
import br.com.unifinancas.domain.cartao.CartaoRepository;
import br.com.unifinancas.domain.fatura.Fatura;
import br.com.unifinancas.domain.fatura.FaturaRepository;
import br.com.unifinancas.domain.fatura.StatusFatura;
import br.com.unifinancas.domain.lancamento.LancamentoFinanceiro;
import br.com.unifinancas.domain.lancamento.LancamentoRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-17, RN-11, ADR-017, SAD Sec4.6: consulta e pagamento (total/parcial, com juros rotativo) de Fatura. */
@Service
public class FaturaService {

    private static final int ESCALA_MONETARIA = 2;
    private static final BigDecimal CEM = new BigDecimal("100");

    private final FaturaRepository faturaRepository;
    private final CartaoRepository cartaoRepository;
    private final LancamentoRepository lancamentoRepository;

    public FaturaService(FaturaRepository faturaRepository, CartaoRepository cartaoRepository,
            LancamentoRepository lancamentoRepository) {
        this.faturaRepository = faturaRepository;
        this.cartaoRepository = cartaoRepository;
        this.lancamentoRepository = lancamentoRepository;
    }

    /** SAD Sec8.2: leitura e permitida a qualquer perfil, inclusive VISUALIZADOR. Mais recente primeiro (Sec7.2). */
    public List<FaturaResumo> listar(TenantContext contexto, UUID cartaoId) {
        buscarCartao(contexto, cartaoId);
        return faturaRepository.findAllByCartaoIdAndAmbienteFinanceiroId(cartaoId, contexto.ambienteFinanceiroId()).stream()
                .map(this::paraResumo)
                .toList();
    }

    /**
     * RN-11, ADR-017: paga total ou parcialmente. Quando o pagamento nao
     * quita a Fatura (status volta `PAGA_PARCIAL`), cria um `Lancamento`
     * `DESPESA` de origem FATURA com o juros rotativo do Cartao sobre o
     * saldo remanescente **apos** este pagamento - registrado a parte
     * (nao somado a `valorTotal`/`valorPago` da Fatura, RN-11: "nao
     * misturado ao valor original das compras"). Sem juros (taxa 0 ou
     * saldo remanescente zerado) nao cria Lancamento algum.
     * {@code dataVencimento} do juros = a mesma da Fatura (decisao tecnica
     * desta tarefa - RN-11/ADR-017 nao especificam, e o juros nasce do
     * ciclo desta Fatura).
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public FaturaResumo pagar(TenantContext contexto, UUID cartaoId, UUID faturaId, BigDecimal valor, LocalDate dataPagamento) {
        Cartao cartao = buscarCartao(contexto, cartaoId);
        Fatura fatura = buscarFatura(contexto, cartaoId, faturaId);

        fatura.pagar(valor);
        faturaRepository.save(fatura);

        if (fatura.getStatus() == StatusFatura.PAGA_PARCIAL) {
            criarLancamentoDeJuros(contexto, cartao, fatura, dataPagamento);
        }
        return paraResumo(fatura);
    }

    /**
     * RN-11, ADR-017: parcela o saldo remanescente de uma Fatura `ATRASADA`
     * em N parcelas iguais (usuario escolhe N), cada uma virando um
     * `Lancamento` `DESPESA` novo, vinculado a esta Fatura, com vencimento
     * mensal sucessivo a partir de hoje (decisao tecnica desta tarefa -
     * ADR-017 nao especifica a partir de qual data contar, e a Fatura ja
     * esta atrasada, entao contar a partir do vencimento original poderia
     * gerar parcela ja vencida). Ultima parcela absorve o resto da divisao
     * (a soma das parcelas bate exatamente com o saldo remanescente).
     * Nao inclui juros ja lancados separadamente (RN-11: sao Lancamentos a
     * parte, pagos por conta propria) - so o saldo da propria Fatura.
     * Ao final, a Fatura e marcada como `PAGA` (mesmo metodo `pagar` -
     * a obrigacao "migrou" para as N parcelas, a Fatura original nao tem
     * mais saldo em aberto).
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public FaturaResumo parcelar(TenantContext contexto, UUID cartaoId, UUID faturaId, int numeroParcelas) {
        if (numeroParcelas < 2) {
            throw new RegraDeNegocioException(
                    "NUMERO_PARCELAS_INVALIDO", "numeroParcelas deve ser pelo menos 2 (parcelar em 1x nao faz sentido).");
        }
        Cartao cartao = buscarCartao(contexto, cartaoId);
        Fatura fatura = buscarFatura(contexto, cartaoId, faturaId);
        if (!fatura.podeSerParcelada()) {
            throw new RegraDeNegocioException(
                    "FATURA_NAO_PODE_SER_PARCELADA", "So e possivel parcelar uma Fatura ATRASADA.");
        }

        BigDecimal saldoAParcelar = fatura.saldoRemanescente();
        List<BigDecimal> valoresDasParcelas = DivisorDeParcelas.dividirEmParcelasIguais(saldoAParcelar, numeroParcelas);
        List<LancamentoFinanceiro> parcelas = new ArrayList<>();
        for (int indice = 0; indice < numeroParcelas; indice++) {
            String descricao = "Parcela " + (indice + 1) + "/" + numeroParcelas + " - Fatura " + fatura.getCompetencia();
            LocalDate vencimentoParcela = LocalDate.now().plusMonths(indice + 1);
            parcelas.add(LancamentoFinanceiro.novoDeFatura(contexto.ambienteFinanceiroId(), cartao.getId(), fatura.getId(),
                    valoresDasParcelas.get(indice), LocalDate.now(), vencimentoParcela, descricao));
        }
        lancamentoRepository.saveAll(parcelas);

        fatura.pagar(saldoAParcelar);
        faturaRepository.save(fatura);
        return paraResumo(fatura);
    }

    private void criarLancamentoDeJuros(TenantContext contexto, Cartao cartao, Fatura fatura, LocalDate dataPagamento) {
        BigDecimal juros = fatura.saldoRemanescente()
                .multiply(cartao.getTaxaJurosRotativoMensal())
                .divide(CEM, ESCALA_MONETARIA, RoundingMode.HALF_UP);
        if (juros.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String descricao = "Juros rotativo - Fatura " + fatura.getCompetencia();
        LancamentoFinanceiro juro = LancamentoFinanceiro.novoDeFatura(contexto.ambienteFinanceiroId(), cartao.getId(),
                fatura.getId(), juros, dataPagamento, fatura.getDataVencimento(), descricao);
        lancamentoRepository.save(juro);
    }

    private Cartao buscarCartao(TenantContext contexto, UUID cartaoId) {
        return cartaoRepository.findByIdAndAmbienteFinanceiroId(cartaoId, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CARTAO_NAO_ENCONTRADO", "Cartao nao encontrado neste Ambiente Financeiro."));
    }

    /** SAD Sec7.2: o path e /v1/cartoes/{cartaoId}/faturas/{faturaId} - a Fatura precisa pertencer ao Cartao do path, nao so ao Ambiente. */
    private Fatura buscarFatura(TenantContext contexto, UUID cartaoId, UUID faturaId) {
        Fatura fatura = faturaRepository.findByIdAndAmbienteFinanceiroId(faturaId, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "FATURA_NAO_ENCONTRADA", "Fatura nao encontrada neste Ambiente Financeiro."));
        if (!fatura.getCartaoId().equals(cartaoId)) {
            throw new RecursoNaoEncontradoException("FATURA_NAO_ENCONTRADA", "Fatura nao encontrada neste Ambiente Financeiro.");
        }
        return fatura;
    }

    private FaturaResumo paraResumo(Fatura fatura) {
        return new FaturaResumo(fatura.getId(), fatura.getCartaoId(), fatura.getCompetencia(), fatura.getDataFechamento(),
                fatura.getDataVencimento(), fatura.getValorTotal(), fatura.getValorPago(), fatura.getStatus());
    }
}
