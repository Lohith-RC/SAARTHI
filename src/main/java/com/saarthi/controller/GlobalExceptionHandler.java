package com.saarthi.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Uniform Error Envelope Handler (resolves P1-UX-001):
 * Provides consistent { "error": { "code", "message", ... } } payloads across all REST endpoints
 * and prevents internal exception/stack traces from leaking to clients on 500 errors.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a,
                        LinkedHashMap::new));

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("code", "VALIDATION_FAILED");
        errorBody.put("message", "Validation constraints failed on request payload");
        errorBody.put("details", details);

        return ResponseEntity.badRequest().body(Map.of("error", errorBody));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("code", "BAD_REQUEST");
        errorBody.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", errorBody));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("[UNHANDLED EXCEPTION] Internal server failure: ", ex);

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("code", "INTERNAL_SERVER_ERROR");
        errorBody.put("message", "An unexpected server error occurred. Please contact the system operator.");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", errorBody));
    }
}