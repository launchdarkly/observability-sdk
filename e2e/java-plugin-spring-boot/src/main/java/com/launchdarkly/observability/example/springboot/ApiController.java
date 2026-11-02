package com.launchdarkly.observability.example.springboot;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.LDClient;
import io.opentelemetry.api.trace.Span;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Routes that exercise the plugin: every handler evaluates a flag (recorded as
 * a span event by the plugin's hook), and some record errors.
 */
@RestController
public class ApiController {
    private final LDClient client;

    public ApiController(LDClient client) {
        this.client = client;
    }

    @GetMapping("/")
    public String index(@RequestHeader(value = "X-User-Key", required = false) String userKey) {
        boolean flagged = client.boolVariation("example-greeting", context(userKey), false);
        return (flagged ? "Hello from the flagged path" : "Hello, World!") + "\n";
    }

    @GetMapping("/rolldice")
    public Map<String, Object> rollDice(@RequestHeader(value = "X-User-Key", required = false) String userKey) {
        LDContext context = context(userKey);
        int sides = client.intVariation("dice-sides", context, 6);
        int roll = ThreadLocalRandom.current().nextInt(1, sides + 1);
        Span.current().setAttribute("dice.roll", roll);
        return Map.of("roll", roll, "sides", sides, "user", context.getKey());
    }

    @GetMapping("/flags/{key}")
    public Map<String, Object> flag(@PathVariable String key,
                                    @RequestHeader(value = "X-User-Key", required = false) String userKey) {
        LDContext context = context(userKey);
        return Map.of("key", key, "value", client.jsonValueVariation(key, context, com.launchdarkly.sdk.LDValue.ofNull()).toJsonString());
    }

    @GetMapping("/error")
    public String error(@RequestHeader(value = "X-User-Key", required = false) String userKey) {
        if (client.boolVariation("soft-errors", context(userKey), false)) {
            return "soft error recorded\n";
        }
        throw new IllegalStateException("random error from the Spring Boot example");
    }

    private static LDContext context(String userKey) {
        String key = userKey == null || userKey.isEmpty()
                ? "user-" + ThreadLocalRandom.current().nextInt(1000)
                : userKey;
        return LDContext.builder(key).build();
    }
}
