package br.com.unifinancas.infrastructure.ocr;

import br.com.unifinancas.application.documento.EntradaExtracao;
import br.com.unifinancas.application.documento.ProcessamentoDocumentoPort;
import br.com.unifinancas.application.documento.ResultadoExtracao;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementação de {@link ProcessamentoDocumentoPort} (ADR-006/ADR-016):
 * cadeia de responsabilidade local-primeiro. Percorre as {@link EtapaExtracao}
 * na ordem fixa XML → PDFBox → OCR → OpenAI (definida por {@code @Order} em
 * cada etapa) e para na primeira que devolve um resultado com {@code valor}.
 *
 * <p>Nenhuma etapa lança para fora: quando não se aplica ou não consegue,
 * devolve vazio e a cascata segue (SAD Seção 7.6, "falha para dentro"). Se
 * nenhuma etapa produzir um valor — inclusive quando a etapa OpenAI está
 * desabilitada ou indisponível — o resultado é {@link ResultadoExtracao#falha}
 * e o orquestrador (T3.1.4) move o processamento para {@code ERRO} +
 * {@code FALHA_PROCESSAMENTO}, nunca criando Lançamento (RN-08).
 */
@Component
public class ProcessamentoDocumentoCascataAdapter implements ProcessamentoDocumentoPort {

    private static final Logger log = LoggerFactory.getLogger(ProcessamentoDocumentoCascataAdapter.class);

    private final List<EtapaExtracao> etapas;
    private final DadosExtraidosMapper mapper;

    public ProcessamentoDocumentoCascataAdapter(List<EtapaExtracao> etapas, DadosExtraidosMapper mapper) {
        this.etapas = etapas;
        this.mapper = mapper;
    }

    @Override
    public ResultadoExtracao extrair(EntradaExtracao entrada) {
        for (EtapaExtracao etapa : etapas) {
            if (!etapa.aceita(entrada)) {
                continue;
            }
            Optional<ExtracaoEtapa> resultado;
            try {
                resultado = etapa.extrair(entrada);
            } catch (RuntimeException e) {
                log.warn("Etapa {} lançou inesperadamente - cascata segue.", etapa.origem(), e);
                continue;
            }
            if (resultado.isPresent()) {
                log.info("Documento '{}' extraído pela etapa {}.", entrada.nomeArquivo(), etapa.origem());
                String json = mapper.toJson(resultado.get(), entrada.tipo(), etapa.origem());
                return ResultadoExtracao.sucesso(json, etapa.origem());
            }
        }
        String motivo = "Nenhuma etapa da cascata extraiu um valor do documento"
                + (entrada.fallbackIaHabilitado() ? "." : " (fallback de IA desabilitado para o Ambiente Financeiro).");
        log.info("Falha na extração de '{}': {}", entrada.nomeArquivo(), motivo);
        return ResultadoExtracao.falha(motivo);
    }
}
