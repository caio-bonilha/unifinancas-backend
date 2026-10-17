package br.com.unifinancas.presentation.dto;

import br.com.unifinancas.domain.movimentacao.TipoMovimentacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** SAD §7.2 - POST /v1/movimentacoes (ADR-014): cria uma Movimentacao avulsa. categoriaId e opcional. */
public record CriarMovimentacaoRequest(
        @NotNull(message = "contaBancariaId e obrigatorio") UUID contaBancariaId,
        @NotNull(message = "tipo e obrigatorio") TipoMovimentacao tipo,
        @NotNull(message = "valor e obrigatorio") BigDecimal valor,
        @NotNull(message = "data e obrigatoria") LocalDate data,
        @NotBlank(message = "descricao e obrigatoria") String descricao,
        UUID categoriaId) {
}
