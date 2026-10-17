package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.contato.TipoContato;
import java.util.UUID;

/** SAD §7.2, §4.12. */
public record ContatoResponse(
        UUID id, String nome, TipoContato tipo, String documento, String email, String telefone, boolean ativo) {
}
