package br.com.unifinancas.presentation.exception;

import br.com.unifinancas.domain.shared.RecursoConflitanteException;
import br.com.unifinancas.domain.shared.RecursoNaoEncontradoException;
import br.com.unifinancas.domain.shared.RegraDeNegocioException;
import br.com.unifinancas.presentation.dto.ErroDetalhe;
import br.com.unifinancas.presentation.dto.ErroResponse;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handler global de excecoes (SAD Sec13, Sec7.5) - toda excecao vira o mesmo
 * payload de erro padrao, com o status HTTP e o codigo definidos pelo
 * mapeamento da Sec13.2. Erros 5xx sao logados com stack trace completo;
 * erros 4xx em WARN sem stack trace, porque fazem parte do fluxo normal
 * da aplicacao, nao indicam bug (Sec13.3).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroResponse> handleRegraDeNegocio(RegraDeNegocioException ex) {
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, ex.getCodigo(), ex.getMessage(), null, ex, false);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> handleRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        return responder(HttpStatus.NOT_FOUND, ex.getCodigo(), ex.getMessage(), null, ex, false);
    }

    @ExceptionHandler(RecursoConflitanteException.class)
    public ResponseEntity<ErroResponse> handleRecursoConflitante(RecursoConflitanteException ex) {
        return responder(HttpStatus.CONFLICT, ex.getCodigo(), ex.getMessage(), null, ex, false);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleValidacaoDeArgumento(MethodArgumentNotValidException ex) {
        List<ErroDetalhe> detalhes = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErroDetalhe(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDACAO_FALHOU",
                "Um ou mais campos sao invalidos.", detalhes, ex, false);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErroResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ErroDetalhe> detalhes = ex.getConstraintViolations().stream()
                .map(cv -> new ErroDetalhe(cv.getPropertyPath().toString(), cv.getMessage()))
                .toList();
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDACAO_FALHOU",
                "Um ou mais campos sao invalidos.", detalhes, ex, false);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> handleMensagemIlegivel(HttpMessageNotReadableException ex) {
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDACAO_FALHOU",
                "O corpo da requisicao nao pode ser interpretado.", null, ex, false);
    }

    /**
     * [Gap identificado pelo usuario]: login com e-mail/senha incorretos e um
     * caso mais especifico de AuthenticationException (RN implicita do
     * Spring Security) - {@code IdentidadeService.autenticar} ja lanca com
     * uma mensagem amigavel ("E-mail ou senha invalidos."), mas o handler
     * generico abaixo a descartava. Precisa vir antes do handler generico
     * para o Spring escolher o mais especifico.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErroResponse> handleCredenciaisInvalidas(BadCredentialsException ex) {
        return responder(HttpStatus.UNAUTHORIZED, "CREDENCIAIS_INVALIDAS", ex.getMessage(), null, ex, false);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResponse> handleNaoAutenticado(AuthenticationException ex) {
        return responder(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Autenticacao nao realizada", null, ex, false);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResponse> handleSemPermissao(AccessDeniedException ex) {
        return responder(HttpStatus.FORBIDDEN, "PERFIL_INSUFICIENTE",
                "Voce nao tem permissao para executar esta acao.", null, ex, false);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleErroInesperado(Exception ex) {
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO",
                "Ocorreu um erro inesperado. Tente novamente mais tarde.", null, ex, true);
    }

    private ResponseEntity<ErroResponse> responder(HttpStatus status, String codigo, String mensagem,
            List<ErroDetalhe> detalhes, Exception ex, boolean logStackTrace) {
        String traceId = resolveTraceId();
        if (logStackTrace) {
            log.error("[{}] {} - {}", traceId, codigo, mensagem, ex);
        } else {
            log.warn("[{}] {} - {}", traceId, codigo, mensagem);
        }
        ErroResponse corpo = new ErroResponse(codigo, mensagem, detalhes, Instant.now(), traceId);
        return ResponseEntity.status(status).body(corpo);
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
