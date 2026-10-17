package br.com.unifinancas.presentation.rest.seguranca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.unifinancas.application.notificacao.NotificacaoService;
import br.com.unifinancas.application.usuario.IdentidadeService;
import br.com.unifinancas.application.usuario.ResultadoCadastro;
import br.com.unifinancas.domain.notificacao.CanalNotificacao;
import br.com.unifinancas.domain.notificacao.Notificacao;
import br.com.unifinancas.domain.notificacao.TipoNotificacao;
import br.com.unifinancas.domain.usuario.UsuarioRepository;
import br.com.unifinancas.presentation.dto.CategoriaResponse;
import br.com.unifinancas.presentation.dto.ContaBancariaResponse;
import br.com.unifinancas.presentation.dto.ContatoResponse;
import br.com.unifinancas.presentation.dto.LancamentoRecorrenteResponse;
import br.com.unifinancas.presentation.dto.LancamentoResponse;
import br.com.unifinancas.shared.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T1.11.1, SAD Sec15.6/Sec8.7: "usuario do Ambiente Financeiro A tenta
 * acessar recurso do Ambiente Financeiro B por ID direto -> 404" - executado
 * de ponta a ponta (controllers reais, MockMvc, Postgres real via
 * Testcontainers), nao so a garantia estatica do ArchUnit (Sec15.5, ja
 * cobre que todo metodo de repositorio declara ambienteFinanceiroId, mas
 * nao prova que o endpoint HTTP de fato usa esse parametro corretamente
 * ponta a ponta). Cobre cada tipo de recurso com um endpoint de acesso
 * direto por ID (path variable ou referencia no corpo) que ja existe no
 * catalogo (Sec7.2): Categoria, Conta Bancaria (edicao e extrato),
 * Contato, Lancamento, Lancamento Recorrente, Notificacao e Documento
 * (referencia um Lancamento por ID no corpo do upload) e Movimentacao
 * (Transferencia referenciando uma Conta Bancaria de outro tenant).
 * Orcamento/Meta fica de fora - ainda nao tem endpoint de acesso por ID
 * (so listar/criar, T1.4.3).
 */
@AutoConfigureMockMvc
class IsolamentoEntreTenantsIT extends AbstractIntegrationTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdentidadeService identidadeService;

    @Autowired
    private NotificacaoService notificacaoService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private String tokenA;
    private String ambienteA;
    private String tokenB;
    private String ambienteB;
    private UUID usuarioIdA;

    @BeforeEach
    void criarDoisAmbientesDeUsuariosDiferentes() {
        String emailA = UUID.randomUUID() + "@example.com";
        ResultadoCadastro cadastroA = identidadeService.registrar("Usuaria A", emailA, "senha12345");
        ResultadoCadastro cadastroB =
                identidadeService.registrar("Usuario B", UUID.randomUUID() + "@example.com", "senha12345");
        tokenA = "Bearer " + cadastroA.accessToken();
        ambienteA = cadastroA.ambienteFinanceiroId().toString();
        tokenB = "Bearer " + cadastroB.accessToken();
        ambienteB = cadastroB.ambienteFinanceiroId().toString();
        usuarioIdA = usuarioRepository.findByEmail(emailA).orElseThrow().getId();
    }

    @Test
    void categoriaDeOutroAmbienteNaoPodeSerEditadaEVira404() throws Exception {
        CategoriaResponse categoriaA =
                postJson("/v1/categorias", "{\"nome\":\"Moradia\",\"tipo\":\"DESPESA\"}", tokenA, ambienteA,
                        CategoriaResponse.class, 201);

        mockMvc.perform(patch("/v1/categorias/" + categoriaA.id())
                        .header("Authorization", tokenB)
                        .header("X-Ambiente-Financeiro-Id", ambienteB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"EDITAR\",\"nome\":\"Invadido\",\"tipo\":\"DESPESA\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void contaBancariaDeOutroAmbienteNaoPodeSerEditadaEVira404() throws Exception {
        ContaBancariaResponse contaA = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Conta A\",\"tipo\":\"CORRENTE\",\"saldoInicial\":100.00}", tokenA, ambienteA,
                ContaBancariaResponse.class, 201);

        mockMvc.perform(patch("/v1/contas-bancarias/" + contaA.id())
                        .header("Authorization", tokenB)
                        .header("X-Ambiente-Financeiro-Id", ambienteB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"EDITAR\",\"nome\":\"Invadida\",\"tipo\":\"CORRENTE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void extratoDeContaBancariaDeOutroAmbienteVira404() throws Exception {
        ContaBancariaResponse contaA = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Conta A\",\"tipo\":\"CORRENTE\",\"saldoInicial\":0}", tokenA, ambienteA,
                ContaBancariaResponse.class, 201);

        mockMvc.perform(get("/v1/contas-bancarias/" + contaA.id() + "/movimentacoes")
                        .header("Authorization", tokenB)
                        .header("X-Ambiente-Financeiro-Id", ambienteB))
                .andExpect(status().isNotFound());
    }

    @Test
    void contatoDeOutroAmbienteNaoPodeSerEditadoEVira404() throws Exception {
        ContatoResponse contatoA = postJson("/v1/contatos", "{\"nome\":\"Fornecedor A\",\"tipo\":\"FORNECEDOR\"}",
                tokenA, ambienteA, ContatoResponse.class, 201);

        mockMvc.perform(patch("/v1/contatos/" + contatoA.id())
                        .header("Authorization", tokenB)
                        .header("X-Ambiente-Financeiro-Id", ambienteB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"EDITAR\",\"nome\":\"Invadido\",\"tipo\":\"FORNECEDOR\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void lancamentoDeOutroAmbienteNaoPodeSerEditadoEVira404() throws Exception {
        LancamentoResponse lancamentoA = postJson("/v1/lancamentos",
                "{\"tipo\":\"DESPESA\",\"valor\":100.00,\"dataEmissao\":\"2026-08-25\","
                        + "\"dataVencimento\":\"2026-08-25\",\"descricao\":\"Lancamento de A\"}",
                tokenA, ambienteA, LancamentoResponse.class, 201);

        mockMvc.perform(patch("/v1/lancamentos/" + lancamentoA.id())
                        .header("Authorization", tokenB)
                        .header("X-Ambiente-Financeiro-Id", ambienteB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"CANCELAR\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void lancamentoRecorrenteDeOutroAmbienteNaoPodeSerEditadoEVira404() throws Exception {
        LancamentoRecorrenteResponse recorrenteA = postJson("/v1/lancamentos-recorrentes",
                "{\"descricao\":\"Assinatura A\",\"tipo\":\"DESPESA\",\"valor\":39.90,"
                        + "\"frequencia\":\"DIARIA\",\"dataInicio\":\"2026-08-25\"}",
                tokenA, ambienteA, LancamentoRecorrenteResponse.class, 201);

        mockMvc.perform(patch("/v1/lancamentos-recorrentes/" + recorrenteA.id())
                        .header("Authorization", tokenB)
                        .header("X-Ambiente-Financeiro-Id", ambienteB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"DESATIVAR\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void notificacaoDeOutroUsuarioEAmbienteNaoPodeSerMarcadaComoLidaEVira404() throws Exception {
        Notificacao notificacaoA = notificacaoService.criar(UUID.fromString(ambienteA), usuarioIdA,
                TipoNotificacao.OUTRO, "Aviso", "Mensagem de teste", null, null, java.util.Set.of(CanalNotificacao.IN_APP))
                .get(0);

        mockMvc.perform(patch("/v1/notificacoes/" + notificacaoA.getId() + "/lida")
                        .header("Authorization", tokenB)
                        .header("X-Ambiente-Financeiro-Id", ambienteB))
                .andExpect(status().isNotFound());
    }

    /** RF-16: anexar um Documento referencia o Lancamento por ID no corpo do upload, nao na URL - mesmo mecanismo de protecao. */
    @Test
    void anexarDocumentoAUmLancamentoDeOutroAmbienteVira404() throws Exception {
        LancamentoResponse lancamentoA = postJson("/v1/lancamentos",
                "{\"tipo\":\"DESPESA\",\"valor\":50.00,\"dataEmissao\":\"2026-08-25\","
                        + "\"dataVencimento\":\"2026-08-25\",\"descricao\":\"Lancamento de A\"}",
                tokenA, ambienteA, LancamentoResponse.class, 201);

        MockMultipartFile arquivo =
                new MockMultipartFile("arquivo", "recibo.pdf", MediaType.APPLICATION_PDF_VALUE, "conteudo".getBytes());

        mockMvc.perform(multipart("/v1/documentos")
                        .file(arquivo)
                        .param("lancamentoId", lancamentoA.id().toString())
                        .param("tipo", "COMPROVANTE")
                        .header("Authorization", tokenB)
                        .header("X-Ambiente-Financeiro-Id", ambienteB))
                .andExpect(status().isNotFound());
    }

    /** ADR-012, RN-05: Transferencia referenciando uma Conta Bancaria de origem de outro tenant. */
    @Test
    void transferenciaComContaDeOrigemDeOutroAmbienteVira404() throws Exception {
        ContaBancariaResponse contaA = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Conta A\",\"tipo\":\"CORRENTE\",\"saldoInicial\":500.00}", tokenA, ambienteA,
                ContaBancariaResponse.class, 201);
        ContaBancariaResponse contaB = postJson("/v1/contas-bancarias",
                "{\"nome\":\"Conta B\",\"tipo\":\"CORRENTE\",\"saldoInicial\":0}", tokenB, ambienteB,
                ContaBancariaResponse.class, 201);

        String corpo = "{\"contaBancariaOrigemId\":\"" + contaA.id() + "\",\"contaBancariaDestinoId\":\"" + contaB.id()
                + "\",\"valor\":50.00,\"data\":\"2026-08-25\",\"descricao\":\"Tentativa invasao\"}";

        mockMvc.perform(post("/v1/movimentacoes/transferencias")
                        .header("Authorization", tokenB)
                        .header("X-Ambiente-Financeiro-Id", ambienteB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isNotFound());
    }

    private <T> T postJson(String uri, String corpo, String tokenBearer, String ambienteFinanceiroId,
            Class<T> tipoResposta, int statusEsperado) throws Exception {
        MvcResult resultado = mockMvc.perform(post(uri)
                        .header("Authorization", tokenBearer)
                        .header("X-Ambiente-Financeiro-Id", ambienteFinanceiroId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().is(statusEsperado))
                .andReturn();
        return objectMapper.readValue(resultado.getResponse().getContentAsString(), tipoResposta);
    }
}
