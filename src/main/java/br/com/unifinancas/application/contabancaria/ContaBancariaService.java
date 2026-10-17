package br.com.unifinancas.application.contabancaria;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.contabancaria.ContaBancaria;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.contabancaria.TipoContaBancaria;
import br.com.unifinancas.domain.movimentacao.MovimentacaoRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-05, SAD Sec4.4: cadastro de Conta Bancaria de um Ambiente Financeiro. */
@Service
public class ContaBancariaService {

    private final ContaBancariaRepository contaBancariaRepository;
    private final MovimentacaoRepository movimentacaoRepository;

    public ContaBancariaService(ContaBancariaRepository contaBancariaRepository, MovimentacaoRepository movimentacaoRepository) {
        this.contaBancariaRepository = contaBancariaRepository;
        this.movimentacaoRepository = movimentacaoRepository;
    }

    /** SAD Sec8.2: leitura de dados financeiros e permitida a qualquer perfil, inclusive VISUALIZADOR. */
    public List<ContaBancariaResumo> listar(TenantContext contexto) {
        return contaBancariaRepository.findAllByAmbienteFinanceiroId(contexto.ambienteFinanceiroId()).stream()
                .map(this::paraResumo)
                .toList();
    }

    /** SAD Sec8.2: escrita financeira exige OWNER, ADMIN ou MEMBRO - VISUALIZADOR nao pode. */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public ContaBancariaResumo criar(TenantContext contexto, String nome, TipoContaBancaria tipo, BigDecimal saldoInicial) {
        BigDecimal saldo = saldoInicial != null ? saldoInicial : BigDecimal.ZERO;
        ContaBancaria conta = ContaBancaria.novo(contexto.ambienteFinanceiroId(), nome, tipo, saldo);
        contaBancariaRepository.save(conta);
        return paraResumo(conta);
    }

    /** SAD Sec8.2: escrita financeira exige OWNER, ADMIN ou MEMBRO. T1.9.6: so os campos descritivos. */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public ContaBancariaResumo editar(TenantContext contexto, UUID id, String nome, TipoContaBancaria tipo) {
        ContaBancaria conta = buscarOuFalhar(contexto, id);
        conta.editar(nome, tipo);
        contaBancariaRepository.save(conta);
        return paraResumo(conta);
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public ContaBancariaResumo ativar(TenantContext contexto, UUID id) {
        ContaBancaria conta = buscarOuFalhar(contexto, id);
        conta.ativar();
        contaBancariaRepository.save(conta);
        return paraResumo(conta);
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public ContaBancariaResumo desativar(TenantContext contexto, UUID id) {
        ContaBancaria conta = buscarOuFalhar(contexto, id);
        conta.desativar();
        contaBancariaRepository.save(conta);
        return paraResumo(conta);
    }

    private ContaBancaria buscarOuFalhar(TenantContext contexto, UUID id) {
        return contaBancariaRepository.findByIdAndAmbienteFinanceiroId(id, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CONTA_BANCARIA_NAO_ENCONTRADA", "Conta Bancaria nao encontrada neste Ambiente Financeiro."));
    }

    /**
     * [Revisado - ADR-012, RN-12] recomputa saldoAtual do zero - saldoInicial
     * + soma com sinal de todas as Movimentacoes da conta (CREDITO soma,
     * DEBITO subtrai) - nunca um delta incremental, para nao acumular
     * drift. Chamado por {@code LancamentoService} logo apos
     * confirmar/reabrir pagamento ou recebimento, e pelo servico de
     * Transferencia (T1.3a.6) - sempre na mesma transacao que cria/remove a
     * Movimentacao (consistencia forte, nao via evento assincrono).
     */
    @Transactional
    public void recalcularSaldo(UUID ambienteFinanceiroId, UUID contaBancariaId) {
        ContaBancaria conta = contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteFinanceiroId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "CONTA_BANCARIA_NAO_ENCONTRADA", "Conta Bancaria nao encontrada neste Ambiente Financeiro."));

        BigDecimal somaMovimentacoes = movimentacaoRepository.somarComSinalPorConta(ambienteFinanceiroId, contaBancariaId);
        conta.atualizarSaldoAtual(conta.getSaldoInicial().add(somaMovimentacoes));
        contaBancariaRepository.save(conta);
    }

    private ContaBancariaResumo paraResumo(ContaBancaria conta) {
        return new ContaBancariaResumo(conta.getId(), conta.getNome(), conta.getTipo(), conta.getSaldoInicial(),
                conta.getSaldoAtual(), conta.getMoeda(), conta.isAtiva());
    }
}
