package br.com.unifinancas.application.lancamentorecorrente;

import br.com.unifinancas.domain.ambientefinanceiro.PerfilUsuarioAmbiente;
import br.com.unifinancas.domain.categoria.CategoriaRepository;
import br.com.unifinancas.domain.contabancaria.ContaBancariaRepository;
import br.com.unifinancas.domain.lancamento.TipoLancamento;
import br.com.unifinancas.domain.lancamentorecorrente.FrequenciaRecorrencia;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrente;
import br.com.unifinancas.domain.lancamentorecorrente.LancamentoRecorrenteRepository;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RequerPerfil;
import br.com.unifinancas.domain.shared.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-11, SAD Sec4.13: CRUD de Lancamento Recorrente (T1.6.2). Materializacao em Lancamento fica para o RecorrenciaJob (T1.6.3). */
@Service
public class LancamentoRecorrenteService {

    private final LancamentoRecorrenteRepository lancamentoRecorrenteRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContaBancariaRepository contaBancariaRepository;

    public LancamentoRecorrenteService(LancamentoRecorrenteRepository lancamentoRecorrenteRepository,
            CategoriaRepository categoriaRepository, ContaBancariaRepository contaBancariaRepository) {
        this.lancamentoRecorrenteRepository = lancamentoRecorrenteRepository;
        this.categoriaRepository = categoriaRepository;
        this.contaBancariaRepository = contaBancariaRepository;
    }

    /** SAD Sec8.2: leitura e permitida a qualquer perfil, inclusive VISUALIZADOR (mesmo criterio de LancamentoService.listar). */
    public List<LancamentoRecorrenteResumo> listar(TenantContext contexto) {
        return lancamentoRecorrenteRepository.findAllByAmbienteFinanceiroId(contexto.ambienteFinanceiroId()).stream()
                .map(this::paraResumo)
                .toList();
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public LancamentoRecorrenteResumo criar(TenantContext contexto, String descricao, TipoLancamento tipo,
            BigDecimal valor, UUID categoriaId, UUID contaBancariaId,
            FrequenciaRecorrencia frequencia, Integer diaReferencia, LocalDate dataInicio, LocalDate dataFim) {
        validarReferenciasDoAmbiente(contexto, categoriaId, contaBancariaId);

        LancamentoRecorrente recorrente = LancamentoRecorrente.novo(contexto.ambienteFinanceiroId(), descricao, tipo,
                valor, categoriaId, contaBancariaId, frequencia, diaReferencia, dataInicio, dataFim);
        lancamentoRecorrenteRepository.save(recorrente);
        return paraResumo(recorrente);
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public LancamentoRecorrenteResumo editar(TenantContext contexto, UUID id, String descricao, BigDecimal valor,
            UUID categoriaId, UUID contaBancariaId, FrequenciaRecorrencia frequencia,
            Integer diaReferencia, LocalDate dataInicio, LocalDate dataFim) {
        LancamentoRecorrente recorrente = localizar(contexto, id);
        validarReferenciasDoAmbiente(contexto, categoriaId, contaBancariaId);

        recorrente.editar(descricao, valor, categoriaId, contaBancariaId, frequencia,
                diaReferencia, dataInicio, dataFim);
        lancamentoRecorrenteRepository.save(recorrente);
        return paraResumo(recorrente);
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public LancamentoRecorrenteResumo desativar(TenantContext contexto, UUID id) {
        LancamentoRecorrente recorrente = localizar(contexto, id);
        recorrente.desativar();
        lancamentoRecorrenteRepository.save(recorrente);
        return paraResumo(recorrente);
    }

    @RequerPerfil({PerfilUsuarioAmbiente.OWNER, PerfilUsuarioAmbiente.ADMIN, PerfilUsuarioAmbiente.MEMBRO})
    @Transactional
    public LancamentoRecorrenteResumo reativar(TenantContext contexto, UUID id) {
        LancamentoRecorrente recorrente = localizar(contexto, id);
        recorrente.reativar();
        lancamentoRecorrenteRepository.save(recorrente);
        return paraResumo(recorrente);
    }

    private LancamentoRecorrente localizar(TenantContext contexto, UUID id) {
        return lancamentoRecorrenteRepository.findByIdAndAmbienteFinanceiroId(id, contexto.ambienteFinanceiroId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "LANCAMENTO_RECORRENTE_NAO_ENCONTRADO", "Lancamento recorrente nao encontrado neste Ambiente Financeiro."));
    }

    /** SAD Sec8.7/Sec12.4: nunca aceitar id de Categoria/Conta Bancaria de outro tenant (mesmo criterio de LancamentoService). */
    private void validarReferenciasDoAmbiente(TenantContext contexto, UUID categoriaId, UUID contaBancariaId) {
        UUID ambienteId = contexto.ambienteFinanceiroId();
        if (categoriaId != null) {
            categoriaRepository.findByIdAndAmbienteFinanceiroId(categoriaId, ambienteId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "CATEGORIA_NAO_ENCONTRADA", "Categoria nao encontrada neste Ambiente Financeiro."));
        }
        if (contaBancariaId != null) {
            contaBancariaRepository.findByIdAndAmbienteFinanceiroId(contaBancariaId, ambienteId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "CONTA_BANCARIA_NAO_ENCONTRADA", "Conta Bancaria nao encontrada neste Ambiente Financeiro."));
        }
    }

    private LancamentoRecorrenteResumo paraResumo(LancamentoRecorrente recorrente) {
        return new LancamentoRecorrenteResumo(recorrente.getId(), recorrente.getDescricao(), recorrente.getTipo(),
                recorrente.getValor(), recorrente.getCategoriaId(), recorrente.getContaBancariaId(),
                recorrente.getFrequencia(), recorrente.getDiaReferencia(),
                recorrente.getDataInicio(), recorrente.getDataFim(), recorrente.isAtivo());
    }
}
