package br.com.unifinancas.domain.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentoTest {

    private final UUID ambienteFinanceiroId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();

    @Test
    void novoDocumentoNasceComTodosOsCamposPreenchidos() {
        Documento documento = Documento.novo(ambienteFinanceiroId, TipoDocumento.BOLETO, "documentos/abc123.pdf",
                "boleto-luz.pdf", 204_800L, "application/pdf", usuarioId);

        assertThat(documento.getAmbienteFinanceiroId()).isEqualTo(ambienteFinanceiroId);
        assertThat(documento.getTipo()).isEqualTo(TipoDocumento.BOLETO);
        assertThat(documento.getArquivoUrl()).isEqualTo("documentos/abc123.pdf");
        assertThat(documento.getNomeArquivo()).isEqualTo("boleto-luz.pdf");
        assertThat(documento.getTamanhoBytes()).isEqualTo(204_800L);
        assertThat(documento.getMimeType()).isEqualTo("application/pdf");
        assertThat(documento.getEnviadoPorUsuarioId()).isEqualTo(usuarioId);
        assertThat(documento.getCriadoEm()).isNotNull();
    }

    @Test
    void arquivoUrlEmBrancoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Documento.novo(ambienteFinanceiroId, TipoDocumento.COMPROVANTE, "  ",
                "comprovante.png", 1024L, "image/png", usuarioId))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("ARQUIVO_URL_OBRIGATORIO"));
    }

    @Test
    void nomeArquivoEmBrancoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Documento.novo(ambienteFinanceiroId, TipoDocumento.COMPROVANTE, "documentos/x.png",
                null, 1024L, "image/png", usuarioId))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("NOME_ARQUIVO_OBRIGATORIO"));
    }

    @Test
    void mimeTypeEmBrancoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Documento.novo(ambienteFinanceiroId, TipoDocumento.COMPROVANTE, "documentos/x.png",
                "comprovante.png", 1024L, "", usuarioId))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("MIME_TYPE_OBRIGATORIO"));
    }

    @Test
    void tamanhoZeroOuNegativoLancaRegraDeNegocio() {
        assertThatThrownBy(() -> Documento.novo(ambienteFinanceiroId, TipoDocumento.COMPROVANTE, "documentos/x.png",
                "comprovante.png", 0L, "image/png", usuarioId))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TAMANHO_INVALIDO"));

        assertThatThrownBy(() -> Documento.novo(ambienteFinanceiroId, TipoDocumento.COMPROVANTE, "documentos/x.png",
                "comprovante.png", -1L, "image/png", usuarioId))
                .isInstanceOf(RegraDeNegocioException.class)
                .satisfies(e -> assertThat(((RegraDeNegocioException) e).getCodigo()).isEqualTo("TAMANHO_INVALIDO"));
    }
}
