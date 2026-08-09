package com.example.shortener.exception;

import com.example.shortener.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // Everything here is a 4xx — the caller sent something wrong, which is not
    // an application fault. Logged at INFO or DEBUG, never WARN or ERROR:
    // anyone can trigger these at will, and error-level noise from ordinary
    // client mistakes is how alerting stops being trusted.

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ErrorResponse> invalidUrl(InvalidUrlException e) {
        log.info("rejected url: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(AliasAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> aliasTaken(AliasAlreadyExistsException e) {
        log.info("{}", e.getMessage());
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidRequest(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .orElse("invalid request");

        log.debug("rejected request: {}", message);
        return error(HttpStatus.BAD_REQUEST, message);
    }

    // body was missing, or wasn't valid JSON
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e) {
        log.debug("unreadable request body: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "request body is missing or malformed");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }
}