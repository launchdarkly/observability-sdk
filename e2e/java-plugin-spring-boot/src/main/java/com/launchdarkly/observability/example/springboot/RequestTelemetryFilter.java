package com.launchdarkly.observability.example.springboot;

import com.launchdarkly.observability.LDObserve;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * One span, a duration histogram, a request counter and a log line per HTTP
 * request. Controllers run inside the span, so flag evaluations and errors
 * recorded there are children of it.
 */
@Component
public class RequestTelemetryFilter extends OncePerRequestFilter {
    static final AttributeKey<String> ROUTE = AttributeKey.stringKey("http.route");
    static final AttributeKey<String> METHOD = AttributeKey.stringKey("http.request.method");
    static final AttributeKey<Long> STATUS = AttributeKey.longKey("http.response.status_code");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String route = request.getRequestURI();
        long started = System.nanoTime();
        Span span = LDObserve.startSpan(request.getMethod() + " " + route,
                Attributes.of(ROUTE, route, METHOD, request.getMethod()));
        try (Scope ignored = span.makeCurrent()) {
            chain.doFilter(request, response);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            int status = response.getStatus();
            if (status >= 500) {
                span.setStatus(StatusCode.ERROR);
            }
            span.setAttribute(STATUS, (long) status);
            span.end();
            GlobalAttributesRepro.expectGlobal("span " + request.getMethod() + " " + route);

            double millis = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - started) / 1000.0;
            Attributes attrs = Attributes.of(ROUTE, route, STATUS, (long) status);
            LDObserve.recordHistogram("http.server.request.duration_ms", millis, attrs);
            LDObserve.recordIncr("http.server.requests", attrs);
            GlobalAttributesRepro.expectGlobal("metric http.server.requests, route " + route);
            String message = request.getMethod() + " " + route + " -> " + status;
            LDObserve.recordLog(message, status >= 500 ? Severity.ERROR : Severity.INFO,
                    attrs.toBuilder().putAll(GlobalAttributesRepro.PER_CALL_ATTRIBUTES).build());
            GlobalAttributesRepro.expectGlobal("log \"" + message + "\"");
            GlobalAttributesRepro.expectPerCallWins("log \"" + message + "\"");
        }
    }
}
