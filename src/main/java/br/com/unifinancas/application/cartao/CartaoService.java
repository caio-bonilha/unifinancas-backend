package br.com.unifinancas.application.cartao;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.cartao.Cartao;
import br.com.unifinancas.domain.cartao.CartaoRepository;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.fatura.Fatura;
import br.com.unifinancas.domain.fatura.FaturaRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-17, SAD Sec4.5: CRUD de Cartao de um Ambiente Financeiro. */
@Service
public class CartaoService {

    private final CartaoRepository cartaoRepository;
    private final ContaBancariaRepository contaBancariaRepository;
    private final FaturaRepository faturaRepository;

    public CartaoService(CartaoRepository cartaoRepository, ContaBancariaRepository contaBancariaRepository,
            FaturaRepository faturaRepository) {
        this.cartaoRepository = cartaoRepository;
        this.contaBancariaRepository = contaBancariaRepository;
        this.faturaRepository = faturaRepository;
    }

    /** SAD Sec8.2: leitura de dados financeiros e permitida a qualquer perfil, inclusive VISUALIZADOR. */
    public List<CartaoResumo> listar(TenantContext contexto) {
        return cartaoRepository.findAllByAmbienteFinanceiroId(contexto.ambienteFinanceiroId()).stream()
                .map(this::paraResumo)
                .toList();
    }

    /**
     * T2.1.2: cria o Cartao e, na mesma transacao, a primeira Fatura
     * (competencia = mes corrente, ABERTA) - sem isso o Cartao nasceria sem
     * nenhuma Fatura para acumular compras ate o proximo fechamento (job).
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public CartaoResumo criar(TenantContext contexto, UUID contaBancariaId, String nome, BigDecimal limite,
            int diaFechamento, int diaVencimento, BigDecimal taxaJurosRotativoMensal) {
        validarContaBancaria(contexto, contaBancariaId);

        Cartao cartao = Cartao.novo(contexto.ambienteFinanceiroId(), contaBancariaId, nome, limite, diaFechamento,
                diaVencimento, taxaJurosRotativoMensal);
        cartaoRepository.save(cartao);
        faturaRepository.save(Fatura.novoParaCompetencia(cartao, YearMonth.now()));
        return paraResumo(cartao);
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public CartaoResumo editar(TenantContext contexto, UUID id, UUID contaBancariaId, String nome, BigDecimal limite,
            int diaFechamento, int diaVencimento, BigDecimal taxaJurosRotativoMensal) {
        validarContaBancaria(contexto, contaBancariaId);
        Cartao cartao = buscarOuFalhar(contexto, id);
        cartao.editar(contaBancariaId, nome, limite, diaFechamento, diaVencimento, taxaJurosRotativoMensal);
        cartaoRepository.save(cartao);
        return paraResumo(cartao);
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public CartaoResumo ativar(TenantContext contexto, UUID id) {
        Cartao cartao = buscarOuFalhar(contexto, id);
        cartao.ativar();
        cartaoRepository.save(cartao);
        return paraResumo(cartao);
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public CartaoResumo desativar(TenantContext contexto, UUID id) {
        Cartao cartao = buscarOuFalhar(contexto, id);
        cartao.desativar();
        cartaoRepository.save(cartao);
        return paraResumo(cartao);
    }

    private void validarContaBancaria(TenantContext contexto, UUID contaBancariaId) {
        if (contaBancariaId == null) {
            return;
        }
        contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CONTA_BANCARIA_NAO_ENCONTRADA", "Conta Bancaria nao encontrada neste Ambiente Financeiro."));
    }

    private Cartao buscarOuFalhar(TenantContext contexto, UUID id) {
        return cartaoRepository.findByIdAndAmbienteFinanceiroId(id, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CARTAO_NAO_ENCONTRADO", "Cartao nao encontrado neste Ambiente Financeiro."));
    }

    private CartaoResumo paraResumo(Cartao cartao) {
        return new CartaoResumo(cartao.getId(), cartao.getContaBancariaId(), cartao.getNome(), cartao.getLimite(),
                cartao.getDiaFechamento(), cartao.getDiaVencimento(), cartao.isAtivo(), cartao.getTaxaJurosRotativoMensal());
    }
}
