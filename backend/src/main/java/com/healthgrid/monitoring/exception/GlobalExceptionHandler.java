package com.healthgrid.monitoring.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(
        ApplicationException exception,
        HttpServletRequest request) {

        return buildResponse400(
            exception,
            request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
        Exception exception,
        HttpServletRequest request) {
        return buildResponse500(
            exception,
            request
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse400(
            ApplicationException exception,
            HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse (
                exception.getKey(),
                exception.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    private ResponseEntity<ErrorResponse> buildResponse500(
            Exception exception,
            HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse (
                exception.getCause().getMessage(),
                exception.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}
