package br.com.unifinancas.application.ambientefinanceiro;

import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.AmbienteFinanceiroRepository;
import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.ambientefinanceiro.StatusVinculo;
import br.com.unifinancas.domain.ambientefinanceiro.TipoAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiro;
import br.com.unifinancas.domain.ambientefinanceiro.UsuarioAmbienteFinanceiroRepository;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-03: listagem, criacao, edicao e exclusao dos Ambientes Financeiros do usuario. */
@Service
public class AmbienteFinanceiroService {

    private final UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository;
    private final AmbienteFinanceiroRepository ambienteFinanceiroRepository;

    public AmbienteFinanceiroService(UsuarioAmbienteFinanceiroRepository usuarioAmbienteFinanceiroRepository,
            AmbienteFinanceiroRepository ambienteFinanceiroRepository) {
        this.usuarioAmbienteFinanceiroRepository = usuarioAmbienteFinanceiroRepository;
        this.ambienteFinanceiroRepository = ambienteFinanceiroRepository;
    }

    public List<AmbienteFinanceiroResumo> listarAmbientesAtivosDoUsuario(UUID usuarioId) {
        List<UsuarioAmbienteFinanceiro> vinculos =
                usuarioAmbienteFinanceiroRepository.findAllByUsuarioIdAndStatus(usuarioId, StatusVinculo.ATIVO);

        return vinculos.stream().map(this::paraResumo).toList();
    }

    /**
     * SAD Sec7.2 (POST /v1/ambientes): cria um novo Ambiente Financeiro e o
     * usuario que o criou vira OWNER - mesma regra RN-03 do cadastro (T1.1.5):
     * quem cria/e o primeiro vinculo de um ambiente e sempre OWNER, ja ATIVO
     * (sem passar por convite).
     */
    @Transactional
    public AmbienteFinanceiroResumo criar(UUID usuarioId, String nome, TipoAmbienteFinanceiro tipo,
            Integer diasAntecedenciaNotificacaoVencimento) {
        AmbienteFinanceiro ambiente = AmbienteFinanceiro.novo(nome, tipo, diasAntecedenciaNotificacaoVencimento);
        ambienteFinanceiroRepository.save(ambiente);

        UsuarioAmbienteFinanceiro vinculo = UsuarioAmbienteFinanceiro.primeiroVinculo(usuarioId, ambiente.getId());
        usuarioAmbienteFinanceiroRepository.save(vinculo);

        return paraResumo(vinculo, ambiente);
    }

    /**
     * Gap identificado pelo usuario (nao havia como editar um Ambiente
     * Financeiro pela UI nem pela API): so o OWNER pode editar nome/dias de
     * antecedencia (decisao consultada com o usuario).
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER})
    @Transactional
    public AmbienteFinanceiroResumo editar(TenantContext contexto, String nome, Integer diasAntecedenciaNotificacaoVencimento) {
        AmbienteFinanceiro ambiente = localizarAmbiente(contexto.ambienteFinanceiroId());
        ambiente.editar(nome, diasAntecedenciaNotificacaoVencimento);
        ambienteFinanceiroRepository.save(ambiente);

        UsuarioAmbienteFinanceiro vinculo = localizarVinculoAtivo(contexto);
        return paraResumo(vinculo, ambiente);
    }

    /**
     * Gap identificado pelo usuario (nao havia como excluir um Ambiente
     * Financeiro pela UI nem pela API, so via exclusao de toda a conta -
     * RN-13). Decisoes consultadas com o usuario: so o OWNER pode excluir; o
     * Ambiente PESSOAL criado automaticamente no cadastro (T1.1.5) nunca pode
     * ser excluido (so indiretamente, excluindo a conta inteira); mesmo
     * padrao de encerramento da RN-13 para os demais - soft-delete, todos os
     * vinculos ativos (nao so o do OWNER) sao removidos, dados preservados
     * para trilha de auditoria (RNF-07).
     *
     * <p>[Limitacao assumida] {@code tipo} e apenas metadado sem regra de
     * negocio (ADR-008) ate aqui - esta e a primeira regra que passa a
     * depender dele. Como nao ha um jeito de distinguir "o Ambiente Pessoal
     * criado no cadastro" de um outro Ambiente que o usuario tenha criado
     * manualmente com {@code tipo=PESSOAL}, o bloqueio vale para qualquer
     * Ambiente PESSOAL, nao so o original.
     */
    @RequerPerfil({PerfilUsuarioAmbiente.OWNER})
    @Transactional
    public void excluir(TenantContext contexto) {
        AmbienteFinanceiro ambiente = localizarAmbiente(contexto.ambienteFinanceiroId());
        if (ambiente.getTipo() == TipoAmbienteFinanceiro.PESSOAL) {
            throw new RegraDeNegocioException("AMBIENTE_PESSOAL_NAO_PODE_SER_EXCLUIDO",
                    "O Ambiente Financeiro Pessoal nao pode ser excluido - so excluindo a conta inteira.");
        }
        encerrarAmbiente(contexto.ambienteFinanceiroId());
    }

    /**
     * RN-13: encerra o Ambiente e remove o vinculo ativo de TODOS os membros
     * dele, nao so de quem disparou a acao. Reaproveitado por
     * {@code PrivacidadeService.excluirConta} quando o usuario que exclui a
     * propria conta e OWNER de um Ambiente compartilhado.
     */
    @Transactional
    public void encerrarAmbiente(UUID ambienteId) {
        AmbienteFinanceiro ambiente = localizarAmbiente(ambienteId);
        ambiente.encerrar();
        ambienteFinanceiroRepository.save(ambiente);

        List<UsuarioAmbienteFinanceiro> membros =
                usuarioAmbienteFinanceiroRepository.findAllByAmbienteFinanceiroIdAndStatus(ambienteId, StatusVinculo.ATIVO);
        for (UsuarioAmbienteFinanceiro membro : membros) {
            membro.remover();
            usuarioAmbienteFinanceiroRepository.save(membro);
        }
    }

    private AmbienteFinanceiro localizarAmbiente(UUID ambienteId) {
        return ambienteFinanceiroRepository.findById(ambienteId)
                .orElseThrow(() -> new IllegalStateException(
                        "Vinculo aponta para um Ambiente Financeiro inexistente: " + ambienteId));
    }

    private UsuarioAmbienteFinanceiro localizarVinculoAtivo(TenantContext contexto) {
        return usuarioAmbienteFinanceiroRepository
                .findByUsuarioIdAndAmbienteFinanceiroId(contexto.usuarioId(), contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new IllegalStateException(
                        "TenantContext aponta para um vinculo inexistente: " + contexto));
    }

    private AmbienteFinanceiroResumo paraResumo(UsuarioAmbienteFinanceiro vinculo) {
        AmbienteFinanceiro ambiente = localizarAmbiente(vinculo.getAmbienteFinanceiroId());
        return paraResumo(vinculo, ambiente);
    }

    private AmbienteFinanceiroResumo paraResumo(UsuarioAmbienteFinanceiro vinculo, AmbienteFinanceiro ambiente) {
        return new AmbienteFinanceiroResumo(ambiente.getId(), ambiente.getNome(), ambiente.getTipo(), vinculo.getPerfil(),
                ambiente.getDiasAntecedenciaNotificacaoVencimento());
    }
}
