package br.com.unifinancas.presentation.rest.categoria;

import br.com.unifinancas.application.categoria.CategoriaResumo;
import br.com.unifinancas.application.categoria.CategoriaService;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.domain.shared.TenantContext;
import br.com.unifinancas.presentation.dto.AtualizarCategoriaRequest;
import br.com.unifinancas.presentation.dto.CategoriaResponse;
import br.com.unifinancas.presentation.dto.CriarCategoriaRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SAD §7.2, RF-09. */
@RestController
@RequestMapping("/v1/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    /** SAD §7.2: lista em arvore (raizes com subcategorias aninhadas). */
    @GetMapping
    public List<CategoriaResponse> listar(TenantContext contexto) {
        return categoriaService.listarArvore(contexto).stream().map(this::paraResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse criar(TenantContext contexto, @Valid @RequestBody CriarCategoriaRequest request) {
        CategoriaResumo resumo = categoriaService.criar(contexto, request.nome(), request.tipo(), request.categoriaPaiId());
        return paraResponse(resumo);
    }

    /** T1.9.6 (gap identificado ao planejar a tela): uma unica rota cobre editar, ativar e desativar. */
    @PatchMapping("/{id}")
    public CategoriaResponse atualizar(TenantContext contexto, @PathVariable UUID id,
            @Valid @RequestBody AtualizarCategoriaRequest request) {
        CategoriaResumo resumo = switch (request.acao()) {
            case EDITAR -> {
                if (request.nome() == null || request.tipo() == null) {
                    throw new RegraDeNegocioException(
                            "CAMPOS_OBRIGATORIOS_PARA_EDITAR", "nome e tipo sao obrigatorios para a acao EDITAR.");
                }
                yield categoriaService.editar(contexto, id, request.nome(), request.tipo(), request.categoriaPaiId());
            }
            case ATIVAR -> categoriaService.ativar(contexto, id);
            case DESATIVAR -> categoriaService.desativar(contexto, id);
        };
        return paraResponse(resumo);
    }

    private CategoriaResponse paraResponse(CategoriaResumo resumo) {
        List<CategoriaResponse> subcategorias = resumo.subcategorias().stream().map(this::paraResponse).toList();
        return new CategoriaResponse(resumo.id(), resumo.nome(), resumo.tipo(), resumo.categoriaPaiId(), resumo.ativa(),
                subcategorias);
    }
}
