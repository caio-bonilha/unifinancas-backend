package br.com.unifinancas.infrastructure.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OfxParserTest {

    /** OFX 1.x (SGML) - a maioria dos bancos brasileiros - sem tag de fechamento nos elementos-folha. */
    private static final String OFX_1X = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102
            SECURITY:NONE
            ENCODING:USASCII
            CHARSET:1252
            COMPRESSION:NONE
            OLDFILEUID:NONE
            NEWFILEUID:NONE

            <OFX>
            <BANKMSGSRSV1>
            <STMTTRNRS>
            <STMTRS>
            <CURDEF>BRL
            <BANKACCTFROM>
            <BANKID>001
            <ACCTID>12345-6
            </BANKACCTFROM>
            <BANKTRANLIST>
            <DTSTART>20260801
            <DTEND>20260831
            <STMTTRN>
            <TRNTYPE>DEBIT
            <DTPOSTED>20260805120000[-3:BRT]
            <TRNAMT>-45.90
            <FITID>2026080500001
            <NAME>PADARIA & CIA
            <MEMO>Compra no debito
            </STMTTRN>
            <STMTTRN>
            <TRNTYPE>CREDIT
            <DTPOSTED>20260810080000[-3:BRT]
            <TRNAMT>1500.00
            <FITID>2026081000002
            <NAME>Salario
            </STMTTRN>
            </BANKTRANLIST>
            </STMTRS>
            </STMTTRNRS>
            </BANKMSGSRSV1>
            </OFX>
            """;

    /** OFX 2.x (XML valido) - mesmo conteudo logico, ja com as tags fechadas. */
    private static final String OFX_2X = """
            <?xml version="1.0" encoding="UTF-8"?>
            <?OFX OFXHEADER="200" VERSION="200" SECURITY="NONE" OLDFILEUID="NONE" NEWFILEUID="NONE"?>
            <OFX>
            <BANKMSGSRSV1>
            <STMTTRNRS>
            <STMTRS>
            <CURDEF>BRL</CURDEF>
            <BANKACCTFROM>
            <BANKID>341</BANKID>
            <ACCTID>98765-4</ACCTID>
            </BANKACCTFROM>
            <BANKTRANLIST>
            <DTSTART>20260901</DTSTART>
            <DTEND>20260930</DTEND>
            <STMTTRN>
            <TRNTYPE>CHECK</TRNTYPE>
            <DTPOSTED>20260903090000</DTPOSTED>
            <TRNAMT>-120.50</TRNAMT>
            <FITID>abc-1</FITID>
            <NAME>Cheque 42</NAME>
            </STMTTRN>
            </BANKTRANLIST>
            </STMTRS>
            </STMTTRNRS>
            </BANKMSGSRSV1>
            </OFX>
            """;

    @Test
    void parseiaOfx1xSgmlComDuasTransacoes() {
        ExtratoOfx extrato = OfxParser.parse(OFX_1X);

        assertThat(extrato.instituicaoId()).isEqualTo("001");
        assertThat(extrato.numeroConta()).isEqualTo("12345-6");
        assertThat(extrato.moeda()).isEqualTo("BRL");
        assertThat(extrato.dataInicio()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(extrato.dataFim()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(extrato.transacoes()).hasSize(2);

        TransacaoOfx primeira = extrato.transacoes().get(0);
        assertThat(primeira.identificadorExterno()).isEqualTo("2026080500001");
        assertThat(primeira.data()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(primeira.valor()).isEqualByComparingTo("-45.90");
        assertThat(primeira.tipo()).isEqualTo(TipoTransacaoOfx.DEBIT);
        assertThat(primeira.descricao()).isEqualTo("PADARIA & CIA - Compra no debito");

        TransacaoOfx segunda = extrato.transacoes().get(1);
        assertThat(segunda.valor()).isEqualByComparingTo("1500.00");
        assertThat(segunda.tipo()).isEqualTo(TipoTransacaoOfx.CREDIT);
        assertThat(segunda.descricao()).isEqualTo("Salario");
    }

    @Test
    void parseiaOfx2xXmlValido() {
        ExtratoOfx extrato = OfxParser.parse(OFX_2X);

        assertThat(extrato.instituicaoId()).isEqualTo("341");
        assertThat(extrato.numeroConta()).isEqualTo("98765-4");
        assertThat(extrato.transacoes()).hasSize(1);
        assertThat(extrato.transacoes().get(0).tipo()).isEqualTo(TipoTransacaoOfx.CHECK);
        assertThat(extrato.transacoes().get(0).valor()).isEqualByComparingTo("-120.50");
    }

    @Test
    void detectaCharsetDoCabecalhoAoParsearBytes() {
        byte[] bytes = OFX_1X.getBytes(Charset.forName("windows-1252"));

        ExtratoOfx extrato = OfxParser.parse(bytes);

        assertThat(extrato.transacoes()).hasSize(2);
    }

    @Test
    void trnTypeDesconhecidoViraOutro() {
        String ofx = OFX_1X.replace("<TRNTYPE>DEBIT", "<TRNTYPE>ALGO_INVENTADO_PELO_BANCO");

        ExtratoOfx extrato = OfxParser.parse(ofx);

        assertThat(extrato.transacoes().get(0).tipo()).isEqualTo(TipoTransacaoOfx.OTHER);
    }

    @Test
    void arquivoSemTagAlgumaLancaOfxParseException() {
        assertThatThrownBy(() -> OfxParser.parse("isto nao e um OFX"))
                .isInstanceOf(OfxParseException.class);
    }

    @Test
    void arquivoComRaizDiferenteDeOfxLancaOfxParseException() {
        assertThatThrownBy(() -> OfxParser.parse("<NAOEOFX><A>1</A></NAOEOFX>"))
                .isInstanceOf(OfxParseException.class);
    }

    @Test
    void transacaoSemDtpostedLancaOfxParseException() {
        String ofx = OFX_1X.replace("<DTPOSTED>20260805120000[-3:BRT]\n", "");

        assertThatThrownBy(() -> OfxParser.parse(ofx))
                .isInstanceOf(OfxParseException.class)
                .hasMessageContaining("DTPOSTED");
    }

    @Test
    void valorInvalidoLancaOfxParseException() {
        String ofx = OFX_1X.replace("<TRNAMT>-45.90", "<TRNAMT>nao-e-numero");

        assertThatThrownBy(() -> OfxParser.parse(ofx))
                .isInstanceOf(OfxParseException.class);
    }
}
