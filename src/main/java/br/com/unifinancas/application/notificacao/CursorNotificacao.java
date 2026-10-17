package br.com.unifinancas.application.notificacao;

import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Codifica/decodifica o cursor opaco de {@code GET /v1/notificacoes} (SAD
 * Sec7.1/Sec17.4: paginacao por cursor, nao offset). Mesma logica de
 * {@code CursorLancamento} (T1.3.5), adaptada para ordenacao mais-recente-
 * primeiro: o par (criadoEm, id) e a chave de ordenacao/paginacao, ambos
 * decrescentes.
 */
record CursorNotificacao(Instant criadoEm, UUID id) {

    String codificar() {
        String bruto = criadoEm + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bruto.getBytes(StandardCharsets.UTF_8));
    }

    static CursorNotificacao decodificar(String cursor) {
        try {
            String bruto = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] partes = bruto.split("\\|", 2);
            return new CursorNotificacao(Instant.parse(partes[0]), UUID.fromString(partes[1]));
        } catch (RuntimeException e) {
            throw new RegraDeNegocioException("CURSOR_INVALIDO", "Cursor de paginacao invalido.");
        }
    }
}
