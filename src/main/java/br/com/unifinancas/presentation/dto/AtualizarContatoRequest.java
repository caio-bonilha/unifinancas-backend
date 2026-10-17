package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.contato.TipoContato;
import jakarta.validation.constraints.NotNull;

/**
 * T1.9.6 - PATCH /v1/contatos/{id}. Campos descritivos so sao obrigatorios
 * quando {@code acao=EDITAR} (validado no controller).
 */
public record AtualizarContatoRequest(
        @NotNull(message = "acao e obrigatoria") AcaoContato acao, String nome, TipoContato tipo, String documento,
        String email, String telefone) {
}
