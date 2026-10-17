package br.com.unifinancas.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class ExtratorDeCamposTest {

    private static final LocalDate BASE = LocalDate.of(1997, 10, 7);

    // Linha digitavel de cobranca bancaria: [0,33) prefixo, [33,37) fator de
    // vencimento, [37,47) valor em centavos.
    private static final String PREFIXO_33 = "237933812860082665995090000633050";
    // Vencimento 15/06/2024: ciclo antigo (antes do reinicio de 22/02/2025).
    private static final LocalDate VENC_CICLO_ANTIGO = LocalDate.of(2024, 6, 15);
    private static final String LINHA_CICLO_ANTIGO =
            PREFIXO_33 + fator(VENC_CICLO_ANTIGO) + "0000026035";
    // fator 1565: ciclo novo (reinicio 22/02/2025) => 10/09/2026.
    private static final String LINHA_VENC_2026 = PREFIXO_33 + "1565" + "0000026035";

    private static String fator(LocalDate vencimento) {
        return String.format("%04d", ChronoUnit.DAYS.between(BASE, vencimento));
    }

    @Test
    void valorSaiDasPosicoes38a47DaLinhaDigitavel() {
        assertThat(ExtratorDeCampos.valorDaLinhaDigitavel(LINHA_CICLO_ANTIGO)).contains(new BigDecimal("260.35"));
    }

    @Test
    void valorZeradoNaLinhaDigitavelResultaEmVazio() {
        assertThat(ExtratorDeCampos.valorDaLinhaDigitavel(PREFIXO_33 + fator(VENC_CICLO_ANTIGO) + "0000000000"))
                .isEmpty();
    }

    @Test
    void vencimentoSaiDoFatorNoCicloAntigo() {
        assertThat(ExtratorDeCampos.vencimentoDaLinhaDigitavel(LINHA_CICLO_ANTIGO)).contains(VENC_CICLO_ANTIGO);
    }

    @Test
    void vencimentoNoCicloNovoUsaBase22Fev2025() {
        assertThat(ExtratorDeCampos.vencimentoDaLinhaDigitavel(LINHA_VENC_2026))
                .contains(LocalDate.of(2026, 9, 10));
    }

    @Test
    void fatorZeroNaoTemVencimento() {
        assertThat(ExtratorDeCampos.vencimentoDaLinhaDigitavel(PREFIXO_33 + "0000" + "0000026035")).isEmpty();
    }

    @Test
    void linhaDigitavelComTamanhoErradoResultaEmVazio() {
        assertThat(ExtratorDeCampos.valorDaLinhaDigitavel("123")).isEmpty();
        assertThat(ExtratorDeCampos.vencimentoDaLinhaDigitavel(null)).isEmpty();
    }

    @Test
    void acharLinhaDigitavelIgnoraPontosEEspacosDoTextoImpresso() {
        String impresso = "Pague: 23793.38128 60082.665995 09000.063305 4 84350000026035\noutra linha";
        assertThat(ExtratorDeCampos.acharLinhaDigitavelCobranca(impresso))
                .contains("23793381286008266599509000063305484350000026035");
    }

    @Test
    void valorDoCodigoDeArrecadacaoSaiDasPosicoes5a15DoCodigoDeBarras() {
        // Conta de energia (48 dígitos, começa com 8): valor efetivo = 25326 centavos.
        String energia = "83650000002.8.53260000000.0.12345678901.2.23456789012.3";
        assertThat(ExtratorDeCampos.acharCodigoArrecadacao(energia))
                .hasValueSatisfying(c -> assertThat(c).hasSize(48).startsWith("8"));
        assertThat(ExtratorDeCampos.valorDoCodigoArrecadacao("836500000028532600000000123456789012234567890123"))
                .contains(new BigDecimal("253.26"));
    }

    @Test
    void brParaDecimalConverteFormatoBrasileiro() {
        assertThat(ExtratorDeCampos.brParaDecimal("1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(ExtratorDeCampos.brParaDecimal("0,99")).isEqualByComparingTo("0.99");
    }

    @Test
    void primeiraDataLeFormatoBrasileiroEIso() {
        assertThat(ExtratorDeCampos.primeiraData("Vencimento 10/09/2026")).contains(LocalDate.of(2026, 9, 10));
        assertThat(ExtratorDeCampos.primeiraData("emissao 2026-08-25")).contains(LocalDate.of(2026, 8, 25));
    }

    @Test
    void extrairSobreTextoDeBoletoAchaValorVencimentoEBeneficiario() {
        String texto = "Banco do Exemplo\n"
                + "Beneficiario: ENEL DISTRIBUICAO SP\n"
                + LINHA_VENC_2026 + "\n"
                + "Vencimento: 10/09/2026\n"
                + "Valor do documento: 260,35\n";

        ExtracaoEtapa e = ExtratorDeCampos.extrair(texto, texto, null);

        assertThat(e.temValor()).isTrue();
        assertThat(e.valor()).isEqualByComparingTo("260.35");
        assertThat(e.vencimento()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(e.linhaDigitavel()).isEqualTo(LINHA_VENC_2026);
        assertThat(e.descricao()).isEqualTo("ENEL DISTRIBUICAO SP");
        assertThat(e.textoBruto()).isEqualTo(texto);
    }

    @Test
    void extrairSobreCupomSemLinhaDigitavelUsaMaiorValorRotulado() {
        String texto = """
                MERCADO EXEMPLO LTDA
                CNPJ 12.345.678/0001-90
                Item A 10,00
                Item B 25,50
                TOTAL R$ 35,50
                Emissao 25/08/2026
                """;

        ExtracaoEtapa e = ExtratorDeCampos.extrair(texto, texto, 0.9);

        assertThat(e.valor()).isEqualByComparingTo("35.50");
        assertThat(e.emissao()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(e.documentoFiscal()).isEqualTo("12.345.678/0001-90");
        assertThat(e.linhaDigitavel()).isNull();
        assertThat(e.confiancaOcr()).isEqualTo(0.9);
    }

    /** Regressão: fatura de energia (dados fictícios) - o valor a pagar (253,26) vinha errado (41,94, da linha "TOTAL" de tributos). */
    @Test
    void extrairSobreFaturaDeEnergiaUsaOCodigoDeArrecadacaoENaoALinhaTotalDeTributos() {
        String texto = """
                Protocolo de autorização: 0000000000000000 - 26/08/2026 às 02:59:53
                AGO/2026 02/09/2026 R$ 253,26
                PIS/PASEP COFINS 191,06 191,06 1,23% 5,67% 2,35 10,83
                02/09/20265.123.456.789-01 *******253,26 AGO/2026
                Autenticação Mecânica 83650000002.8.53260000000.0.12345678901.2.23456789012.3
                Energia Elétrica kWh kWh 184 1.26638 233,00 13,18 233,00 18,000 41,94 0,96678
                Contrib Ilum Pública Municipal 25,98
                Bônus ITAIPU-Lei 10438/2002 -11,67
                TOTAL 13,18 41,94
                """;

        ExtracaoEtapa e = ExtratorDeCampos.extrair(texto, texto, null);

        assertThat(e.valor()).isEqualByComparingTo("253.26");
        assertThat(e.vencimento()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(e.linhaDigitavel()).isEqualTo("836500000028532600000000123456789012234567890123");
    }

    /** Regressão: fatura de gás (dados fictícios) - valor a pagar 142,87, não os 28,77 de "VALOR DOS TRIBUTOS". */
    @Test
    void extrairSobreFaturaDeGasUsaOCodigoDeArrecadacaoENaoALinhaDeTributos() {
        String texto = """
                MARIA EXEMPLO DA SILVA AGO/2026 1234567-3
                R$ 142,87 05/10/2026 20/08/2026 123456789
                DATA DA PROXIMA LEITURA 16-09-2026
                VALOR DOS TRIBUTOS                      28,77
                142,87
                R$ 142,87
                83650000001-0  42870000000-0  00001234567-3  12345678901-2
                05/10/2026
                27/08/2026
                """;

        ExtracaoEtapa e = ExtratorDeCampos.extrair(texto, texto, null);

        assertThat(e.valor()).isEqualByComparingTo("142.87");
        assertThat(e.vencimento()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(e.linhaDigitavel()).hasSize(48).startsWith("8");
    }
}
