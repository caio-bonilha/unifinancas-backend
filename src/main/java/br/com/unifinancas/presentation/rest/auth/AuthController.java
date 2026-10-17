package br.com.unifinancas.presentation.rest.auth;

import br.com.unifinancas.application.usuario.IdentidadeService;
import br.com.unifinancas.application.usuario.ResultadoCadastro;
import br.com.unifinancas.application.usuario.ResultadoLogin;
import br.com.unifinancas.presentation.dto.AtivarConviteRequest;
import br.com.unifinancas.presentation.dto.CadastroRequest;
import br.com.unifinancas.presentation.dto.CadastroResponse;
import br.com.unifinancas.presentation.dto.LoginRequest;
import br.com.unifinancas.presentation.dto.RecuperarSenhaRequest;
import br.com.unifinancas.presentation.dto.RedefinirSenhaRequest;
import br.com.unifinancas.presentation.dto.RefreshRequest;
import br.com.unifinancas.presentation.dto.TokensResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SAD §7.2 - {@code /v1/auth/*} e publico (SecurityConfig). */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final IdentidadeService identidadeService;

    public AuthController(IdentidadeService identidadeService) {
        this.identidadeService = identidadeService;
    }

    /** Fluxo 6.1 (SAD): cria Usuario, primeiro Ambiente Financeiro e vinculo OWNER em uma transacao. */
    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public CadastroResponse cadastrar(@Valid @RequestBody CadastroRequest request) {
        ResultadoCadastro resultado = identidadeService.registrar(request.nome(), request.email(), request.senha());
        return new CadastroResponse(
                resultado.accessToken(), resultado.refreshToken(), resultado.ambienteFinanceiroId(), resultado.nome());
    }

    /** SAD §7.2: autentica e retorna tokens (RF-01). */
    @PostMapping("/login")
    public TokensResponse login(@Valid @RequestBody LoginRequest request) {
        ResultadoLogin resultado = identidadeService.autenticar(request.email(), request.senha());
        return new TokensResponse(resultado.accessToken(), resultado.refreshToken(), resultado.nome());
    }

    /** SAD §7.2/ADR-010: renova o access token rotacionando o refresh token. */
    @PostMapping("/refresh")
    public TokensResponse refresh(@Valid @RequestBody RefreshRequest request) {
        ResultadoLogin resultado = identidadeService.renovar(request.refreshToken());
        return new TokensResponse(resultado.accessToken(), resultado.refreshToken(), resultado.nome());
    }

    /**
     * SAD §7.2, §12.5 (RF-04): ativa um convite cujo e-mail ainda nao tinha
     * Usuario - cria o Usuario e o vinculo ATIVO no ambiente do convite na
     * mesma operacao. Publica: o token do convite e quem autoriza a acao.
     */
    @PostMapping("/ativar-convite")
    @ResponseStatus(HttpStatus.CREATED)
    public CadastroResponse ativarConvite(@Valid @RequestBody AtivarConviteRequest request) {
        ResultadoCadastro resultado = identidadeService.ativarConvite(request.token(), request.nome(), request.senha());
        return new CadastroResponse(
                resultado.accessToken(), resultado.refreshToken(), resultado.ambienteFinanceiroId(), resultado.nome());
    }

    /**
     * SAD §7.2, §6.7 (RF-01, RN-14): solicita redefinicao de senha. Responde
     * 202 sem corpo em qualquer caso - nunca revela se o e-mail tem conta
     * (anti-enumeracao). O trabalho real (gerar token + enviar e-mail) so
     * acontece para um Usuario ATIVO.
     */
    @PostMapping("/recuperar-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void recuperarSenha(@Valid @RequestBody RecuperarSenhaRequest request) {
        identidadeService.solicitarRedefinicaoSenha(request.email());
    }

    /**
     * SAD §7.2, §6.7 (RF-01, RN-14): redefine a senha com o token do e-mail.
     * Uso unico; invalida os demais tokens pendentes e revoga todos os
     * refresh tokens do Usuario. Nao faz login automatico - 204 e o cliente
     * vai para a tela de login. Token invalido/expirado/usado -> 422
     * TOKEN_REDEFINICAO_INVALIDO.
     */
    @PostMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest request) {
        identidadeService.redefinirSenha(request.token(), request.novaSenha());
    }
}
