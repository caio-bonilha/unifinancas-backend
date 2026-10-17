package br.com.unifinancas.application.contato;

import br.com.unifinancas.domain.contato.TipoContato;
import java.util.UUID;

public record ContatoResumo(
        UUID id, String nome, TipoContato tipo, String documento, String email, String telefone, boolean ativo) {
}
