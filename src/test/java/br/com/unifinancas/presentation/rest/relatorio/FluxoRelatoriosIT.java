package br.com.unifinancas.presentation.rest.relatorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.presentation.dto.CadastroResponse;
import br.com.unifinancas.presentation.dto.PontoEvolucaoMensalResponse;
import br.com.unifinancas.presentation.dto.PontoFluxoCaixaProjetadoResponse;
import br.com.unifinancas.presentation.dto.RelatorioReceitasDespesasResponse;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * RF-22, ADR-018, T2.4.3: os quatro relatorios respondem com sessao valida,
 * mesmo sem nenhum dado lancado ainda (series zeradas) - atraves dos
 * controllers reais (MockMvc) contra um Postgres real (Testcontainers).
 */
@AutoConfigureMockMvc
class FluxoRelatoriosIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void osQuatroRelatoriosRespondemComSeriesZeradasSemNenhumDadoLancado() throws Exception {
        CadastroResponse cadastro = postJson("/v1/auth/cadastro",
                "{\"nome\":\"Gil Relatorios\",\"email\":\"gil.relatorios@example.com\",\"senha\":\"senha12345\"}", null, null,
                CadastroResponse.class, 201);
        String token = "Bearer " + cadastro.accessToken();
        String ambienteId = cadastro.ambienteFinanceiroId().toString();

        MvcResult resultadoEvolucao = mockMvc.perform(get("/v1/relatorios/evolucao")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andReturn();
        PontoEvolucaoMensalResponse[] evolucao =
                objectMapper.readValue(resultadoEvolucao.getResponse().getContentAsString(), PontoEvolucaoMensalResponse[].class);
        assertThat(evolucao).hasSize(12);
        assertThat(evolucao[11].mes()).isEqualTo(YearMonth.now());
        assertThat(evolucao[11].saldo()).isEqualByComparingTo("0");

        mockMvc.perform(get("/v1/relatorios/categorias")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk());

        MvcResult resultadoReceitasDespesas = mockMvc.perform(get("/v1/relatorios/receitas-despesas")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andReturn();
        RelatorioReceitasDespesasResponse receitasDespesas = objectMapper.readValue(
                resultadoReceitasDespesas.getResponse().getContentAsString(), RelatorioReceitasDespesasResponse.class);
        assertThat(receitasDespesas.resultado()).isEqualByComparingTo("0");

        MvcResult resultadoProjecao = mockMvc.perform(get("/v1/relatorios/fluxo-caixa-projetado")
                        .header("Authorization", token)
                        .header("X-Ambiente-Financeiro-Id", ambienteId))
                .andExpect(status().isOk())
                .andReturn();
        PontoFluxoCaixaProjetadoResponse[] projecao = objectMapper.readValue(
                resultadoProjecao.getResponse().getContentAsString(), PontoFluxoCaixaProjetadoResponse[].class);
        assertThat(projecao).hasSize(12);
        assertThat(projecao[0].mes()).isEqualTo(YearMonth.now());
        assertThat(projecao[0].resultadoProjetado()).isEqualByComparingTo("0");
    }

    private <T> T postJson(String uri, String corpo, String tokenBearer, String ambienteFinanceiroId,
            Class<T> tipoResposta, int statusEsperado) throws Exception {
        var requestBuilder = post(uri).contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (tokenBearer != null) {
            requestBuilder = requestBuilder.header("Authorization", tokenBearer);
        }
        if (ambienteFinanceiroId != null) {
            requestBuilder = requestBuilder.header("X-Ambiente-Financeiro-Id", ambienteFinanceiroId);
        }
        MvcResult resultado = mockMvc.perform(requestBuilder).andExpect(status().is(statusEsperado)).andReturn();
        return objectMapper.readValue(resultado.getResponse().getContentAsString(), tipoResposta);
    }
}
