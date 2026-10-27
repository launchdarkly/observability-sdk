package com.launchdarkly.observability.example.springboot;

import com.launchdarkly.observability.LDObserve;
import io.opentelemetry.api.common.Attributes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Records every unhandled exception through the plugin before answering 500.
 */
@RestControllerAdvice
public class ErrorHandling {

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<String> notFound(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("no route for " + e.getRequestURL() + "\n");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> unhandled(Exception e, HttpServletRequest request) {
        LDObserve.recordError(e, Attributes.of(RequestTelemetryFilter.ROUTE, request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error: " + e.getMessage() + "\n");
    }
}
