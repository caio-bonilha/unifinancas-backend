package br.com.unifinancas.infrastructure.ocr;

import br.com.unifinancas.application.documento.EntradaExtracao;
import br.com.unifinancas.application.documento.OrigemExtracao;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Etapa 4 (última) da cascata (ADR-016): provedor de IA de visão — <b>Gemini
 * por padrão</b>, OpenAI como alternativa ({@code unifinancas.ocr.ia.provedor},
 * {@link VisaoIaConfig}). Só se aplica quando as duas condições da ADR-016
 * são verdadeiras:
 * <ol>
 *   <li>a chave do provedor escolhido está configurada — senão
 *       {@code Optional<ClienteVisaoDocumento>} vem vazio ("bean nulo");</li>
 *   <li>o Ambiente Financeiro habilitou o fallback
 *       ({@code entrada.fallbackIaHabilitado()}, flag {@code ocrFallbackIaHabilitado}).</li>
 * </ol>
 * Falhando as duas, ou a chamada esgotando a resiliência, a cascata termina
 * sem sucesso e o processamento vai para {@code ERRO} (RN-08).
 */
@Component
@Order(40)
class EtapaVisaoIa implements EtapaExtracao {

    private static final Logger log = LoggerFactory.getLogger(EtapaVisaoIa.class);

    private final Optional<ClienteVisaoDocumento> client;
    private final RasterizadorDocumento rasterizador;

    EtapaVisaoIa(Optional<ClienteVisaoDocumento> client, RasterizadorDocumento rasterizador) {
        this.client = client;
        this.rasterizador = rasterizador;
    }

    @Override
    public boolean aceita(EntradaExtracao entrada) {
        return client.isPresent() && entrada.fallbackIaHabilitado();
    }

    @Override
    public Optional<ExtracaoEtapa> extrair(EntradaExtracao entrada) {
        byte[] png;
        try {
            png = rasterizador.paraPng(entrada);
        } catch (IOException e) {
            log.warn("Não foi possível preparar a imagem para a IA de visão.", e);
            return Optional.empty();
        }
        try {
            return client.get().extrair(png, entrada.tipo())
                    .filter(ExtracaoEtapa::temValor);
        } catch (RuntimeException e) {
            // resiliência esgotada (retry/circuit breaker) ou erro HTTP - falha "para dentro".
            log.warn("Etapa de IA de visão falhou ({}) - cascata encerra sem sucesso.", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public OrigemExtracao origem() {
        return OrigemExtracao.IA;
    }
}
