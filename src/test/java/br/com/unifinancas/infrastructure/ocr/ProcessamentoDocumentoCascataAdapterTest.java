package br.com.unifinancas.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.unifinancas.application.documento.EntradaExtracao;
import br.com.unifinancas.application.documento.OrigemExtracao;
import br.com.unifinancas.application.documento.ResultadoExtracao;
import br.com.unifinancas.domain.documento.TipoDocumento;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ProcessamentoDocumentoCascataAdapterTest {

    private final DadosExtraidosMapper mapper = new DadosExtraidosMapper(new ObjectMapper());

    private EntradaExtracao entrada(boolean fallbackIa) {
        return new EntradaExtracao(new byte[] {1, 2, 3}, "application/pdf", "doc.pdf", TipoDocumento.BOLETO, fallbackIa);
    }

    private static ExtracaoEtapa comValor(String valor) {
        return new ExtracaoEtapa(new BigDecimal(valor), null, null, null, null, null, "t", null);
    }

    /** Etapa de teste: decide aceitar e o que devolver. */
    private static EtapaExtracao etapa(OrigemExtracao origem, boolean aceita,
            Function<EntradaExtracao, Optional<ExtracaoEtapa>> resultado) {
        return new EtapaExtracao() {
            @Override
            public boolean aceita(EntradaExtracao e) {
                return aceita;
            }

            @Override
            public Optional<ExtracaoEtapa> extrair(EntradaExtracao e) {
                return resultado.apply(e);
            }

            @Override
            public OrigemExtracao origem() {
                return origem;
            }
        };
    }

    @Test
    void paraNaPrimeiraEtapaQueDevolveValor() {
        var adapter = new ProcessamentoDocumentoCascataAdapter(List.of(
                etapa(OrigemExtracao.XML, true, e -> Optional.empty()),
                etapa(OrigemExtracao.PDFBOX, true, e -> Optional.of(comValor("123.45"))),
                etapa(OrigemExtracao.OCR, true, e -> {
                    throw new AssertionError("etapa seguinte nao deveria ser chamada");
                })), mapper);

        ResultadoExtracao r = adapter.extrair(entrada(false));

        assertThat(r.sucesso()).isTrue();
        assertThat(r.origem()).isEqualTo(OrigemExtracao.PDFBOX);
        assertThat(r.dadosExtraidosJson()).contains("\"origemExtracao\":\"PDFBOX\"").contains("\"valor\":\"123.45\"");
    }

    @Test
    void pulaEtapaQueNaoAceita() {
        var adapter = new ProcessamentoDocumentoCascataAdapter(List.of(
                etapa(OrigemExtracao.XML, false, e -> {
                    throw new AssertionError("nao deveria ser chamada");
                }),
                etapa(OrigemExtracao.PDFBOX, true, e -> Optional.of(comValor("9.99")))), mapper);

        assertThat(adapter.extrair(entrada(false)).origem()).isEqualTo(OrigemExtracao.PDFBOX);
    }

    @Test
    void etapaQueLancaNaoInterrompeACascata() {
        var adapter = new ProcessamentoDocumentoCascataAdapter(List.of(
                etapa(OrigemExtracao.PDFBOX, true, e -> {
                    throw new RuntimeException("bug na lib");
                }),
                etapa(OrigemExtracao.OCR, true, e -> Optional.of(comValor("1.00")))), mapper);

        ResultadoExtracao r = adapter.extrair(entrada(false));

        assertThat(r.sucesso()).isTrue();
        assertThat(r.origem()).isEqualTo(OrigemExtracao.OCR);
    }

    @Test
    void nenhumaEtapaComValorResultaEmFalha() {
        var adapter = new ProcessamentoDocumentoCascataAdapter(List.of(
                etapa(OrigemExtracao.XML, true, e -> Optional.empty()),
                etapa(OrigemExtracao.PDFBOX, true, e -> Optional.empty())), mapper);

        ResultadoExtracao r = adapter.extrair(entrada(true));

        assertThat(r.sucesso()).isFalse();
        assertThat(r.dadosExtraidosJson()).isNull();
        assertThat(r.motivo()).contains("Nenhuma etapa");
    }

    @Test
    void falhaComFallbackDesabilitadoMencionaOMotivo() {
        var adapter = new ProcessamentoDocumentoCascataAdapter(List.of(
                etapa(OrigemExtracao.PDFBOX, true, e -> Optional.empty())), mapper);

        ResultadoExtracao r = adapter.extrair(entrada(false));

        assertThat(r.sucesso()).isFalse();
        assertThat(r.motivo()).contains("fallback de IA desabilitado");
    }
}
