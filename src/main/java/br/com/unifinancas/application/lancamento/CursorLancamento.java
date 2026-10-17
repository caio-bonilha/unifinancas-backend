package br.com.unifinancas.application.lancamento;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

/**
 * Codifica/decodifica o cursor opaco de {@code GET /v1/lancamentos} (SAD
 * Sec7.1, Sec17.4: paginacao por cursor, nao offset, dado que o historico
 * de Lancamentos cresce sem limite). O par (dataVencimento, id) e a chave
 * de ordenacao/paginacao (id como desempate - UUIDv7 e unico e ordenavel,
 * ADR-011); o formato exato do cursor nao e definido no SAD, decidido aqui
 * como base64 de "dataVencimento|id".
 */
record CursorLancamento(LocalDate dataVencimento, UUID id) {

    String codificar() {
        String bruto = dataVencimento + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bruto.getBytes(StandardCharsets.UTF_8));
    }

    static CursorLancamento decodificar(String cursor) {
        try {
            String bruto = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] partes = bruto.split("\\|", 2);
            return new CursorLancamento(LocalDate.parse(partes[0]), UUID.fromString(partes[1]));
        } catch (RuntimeException e) {
            throw new RegraDeNegocioException("CURSOR_INVALIDO", "Cursor de paginacao invalido.");
        }
    }
}
