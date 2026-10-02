package dev.changoff.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(
            Map.of("message", e.getReason() == null ? "Request failed" : e.getReason())
        );
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
    })
    ResponseEntity<?> invalid(Exception e) {
        String message =
            e instanceof IllegalArgumentException
                ? e.getMessage()
                : "Check your input values and try again.";
        return ResponseEntity.badRequest().body(
            Map.of("message", message == null ? "Invalid input" : message)
        );
    }
}
