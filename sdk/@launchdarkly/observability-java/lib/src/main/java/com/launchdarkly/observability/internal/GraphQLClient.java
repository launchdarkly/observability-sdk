package com.launchdarkly.observability.internal;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Minimal GraphQL client on {@link HttpClient} (Java 11) and Gson.
 *
 * <p>Uses the same HTTP stack as the OTLP exporters, so proxy settings and
 * custom headers behave the same for the sampling request as for exports.
 * Instances are immutable and safe to share between threads.</p>
 */
public final class GraphQLClient {

    private static final Logger log = Logger.getLogger(GraphQLClient.class.getName());
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Gson GSON = new Gson();

    private final URI endpoint;
    private final Map<String, String> headers;
    private final HttpClient httpClient;

    /**
     * Creates a client that uses the JVM default proxy selector and sends no
     * extra headers.
     */
    public GraphQLClient(String backendUrl) {
        this(backendUrl, Collections.emptyMap(), null);
    }

    /**
     * @param backendUrl    the GraphQL endpoint
     * @param headers       headers added to every request; may be empty
     * @param proxySelector the proxy selector for requests, or null for the JVM default
     */
    public GraphQLClient(String backendUrl, Map<String, String> headers, ProxySelector proxySelector) {
        this.endpoint = URI.create(backendUrl);
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (proxySelector != null) {
            builder.proxy(proxySelector);
        }
        this.httpClient = builder.build();
    }

    /**
     * Executes a GraphQL query and returns the parsed "data" element.
     *
     * @param query     the GraphQL query string
     * @param variables query variables
     * @return the "data" JSON element from the response, or null on error
     */
    public JsonElement execute(String query, Map<String, Object> variables) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        body.add("variables", GSON.toJsonTree(variables));

        byte[] payload = gzip(GSON.toJson(body).getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Content-Encoding", "gzip")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload));
        for (Map.Entry<String, String> header : headers.entrySet()) {
            request.header(header.getKey(), header.getValue());
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the GraphQL response", e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            log.warning("GraphQL request failed with status " + status);
            return null;
        }

        JsonObject envelope = GSON.fromJson(response.body(), JsonObject.class);
        if (envelope == null) {
            log.warning("GraphQL response had no body");
            return null;
        }
        if (envelope.has("errors") && !envelope.getAsJsonArray("errors").isEmpty()) {
            log.warning("GraphQL errors: " + envelope.getAsJsonArray("errors"));
        }
        return envelope.has("data") ? envelope.get("data") : null;
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
            gzos.write(data);
        }
        return bos.toByteArray();
    }
}
