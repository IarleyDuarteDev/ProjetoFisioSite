package br.com.fisioclinica.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.LinkedHashMap;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice @Slf4j
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ProblemDetail negocio(ApiException ex) { return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacao(MethodArgumentNotValidException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Revise os campos informados.");
        var campos = new LinkedHashMap<String, String>();
        ex.getBindingResult().getFieldErrors().forEach(e -> campos.putIfAbsent(e.getField(), e.getDefaultMessage()));
        problem.setProperty("campos", campos);
        return problem;
    }
    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    ProblemDetail entrada(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Parâmetro ausente ou inválido. Confira o formato e os valores permitidos.");
    }
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    ProblemDetail conflito(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Os dados já existem ou o horário acabou de ser alterado. Atualize e tente novamente.");
    }
    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail inexistente(Exception ex) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Rota não encontrada."); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail metodo(Exception ex) { return ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido."); }
    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex) {
        log.error("Falha inesperada na API: {}", ex.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível concluir a operação. Tente novamente mais tarde.");
    }
}
