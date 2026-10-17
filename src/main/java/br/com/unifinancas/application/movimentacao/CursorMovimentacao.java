package br.com.unifinancas.application.movimentacao;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

/**
 * Codifica/decodifica o cursor opaco de {@code GET /v1/contas-bancarias/{id}/movimentacoes}
 * (SAD Sec7.1, Sec17.4) - mesmo padrao de {@code CursorLancamento} (T1.3.5):
 * (data, id) como chave de ordenacao/paginacao, id como desempate (UUIDv7,
 * ADR-011).
 */
record CursorMovimentacao(LocalDate data, UUID id) {

    String codificar() {
        String bruto = data + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bruto.getBytes(StandardCharsets.UTF_8));
    }

    static CursorMovimentacao decodificar(String cursor) {
        try {
            String bruto = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] partes = bruto.split("\\|", 2);
            return new CursorMovimentacao(LocalDate.parse(partes[0]), UUID.fromString(partes[1]));
        } catch (RuntimeException e) {
            throw new RegraDeNegocioException("CURSOR_INVALIDO", "Cursor de paginacao invalido.");
        }
    }
}
