package br.com.unifinancas.infrastructure.ocr;

import br.com.unifinancas.application.documento.OrigemExtracao;
import br.com.unifinancas.domain.documento.TipoDocumento;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Converte o resultado cru de uma etapa ({@link ExtracaoEtapa}) no JSON de
 * {@code dados_extraidos} ({@link DadosExtraidos}), calculando
 * {@code camposFaltando} conforme o tipo do documento.
 */
@Component
class DadosExtraidosMapper {

    private final ObjectMapper objectMapper;

    DadosExtraidosMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String toJson(ExtracaoEtapa etapa, TipoDocumento tipo, OrigemExtracao origem) {
        DadosExtraidos dados = new DadosExtraidos(
                origem,
                etapa.confiancaOcr(),
                tipo.name(),
                etapa.valor() == null ? null : etapa.valor().toPlainString(),
                iso(etapa.vencimento()),
                iso(etapa.emissao()),
                etapa.descricao(),
                etapa.documentoFiscal(),
                etapa.linhaDigitavel(),
                etapa.textoBruto(),
                camposFaltando(etapa, tipo));
        try {
            return objectMapper.writeValueAsString(dados);
        } catch (JsonProcessingException e) {
            // DadosExtraidos e um record de tipos simples - nao deve acontecer.
            throw new IllegalStateException("Falha ao serializar dados_extraidos.", e);
        }
    }

    private static String iso(LocalDate data) {
        return data == null ? null : data.toString();
    }

    /**
     * Campos "esperados" por tipo (decisão T3.1.3: só {@code valor} força a
     * cascata a seguir; os demais só entram nesta lista para a revisão humana
     * saber o que completar).
     */
    private static List<String> camposFaltando(ExtracaoEtapa etapa, TipoDocumento tipo) {
        List<String> faltando = new ArrayList<>();
        if (etapa.valor() == null) {
            faltando.add("valor");
        }
        if (etapa.descricao() == null) {
            faltando.add("descricao");
        }
        switch (tipo) {
            case BOLETO -> {
                if (etapa.vencimento() == null) {
                    faltando.add("vencimento");
                }
                if (etapa.linhaDigitavel() == null) {
                    faltando.add("linhaDigitavel");
                }
            }
            case NOTA_FISCAL -> {
                if (etapa.emissao() == null) {
                    faltando.add("emissao");
                }
                if (etapa.documentoFiscal() == null) {
                    faltando.add("documentoFiscal");
                }
            }
            case CUPOM_FISCAL -> {
                if (etapa.emissao() == null) {
                    faltando.add("emissao");
                }
            }
            case COMPROVANTE -> {
                if (etapa.emissao() == null && etapa.vencimento() == null) {
                    faltando.add("data");
                }
            }
            default -> {
                // sem campos extras
            }
        }
        return faltando;
    }
}
