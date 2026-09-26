package br.com.fisioclinica.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    public ApiException(HttpStatus status, String mensagem) { super(mensagem); this.status = status; }
    public static ApiException notFound(String mensagem) { return new ApiException(HttpStatus.NOT_FOUND, mensagem); }
    public static ApiException conflict(String mensagem) { return new ApiException(HttpStatus.CONFLICT, mensagem); }
    public static ApiException badRequest(String mensagem) { return new ApiException(HttpStatus.BAD_REQUEST, mensagem); }
}
