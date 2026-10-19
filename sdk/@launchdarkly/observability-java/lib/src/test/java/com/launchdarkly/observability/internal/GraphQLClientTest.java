package com.launchdarkly.observability.internal;

import com.google.gson.JsonElement;
import com.launchdarkly.observability.internal.sampling.SamplingConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphQLClientTest {

    private static final String SAMPLING_RESPONSE =
            "{\"data\":{\"sampling\":{\"spans\":[{\"name\":{\"matchValue\":\"health-check\"},"
                    + "\"attributes\":[],\"events\":[],\"samplingRatio\":0}],\"logs\":[]}}}";

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> headerValues = new CopyOnWriteArrayList<>();
    private final List<String> requestLines = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String response = SAMPLING_RESPONSE;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requestLines.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            String header = exchange.getRequestHeaders().getFirst("X-Test-Header");
            headerValues.add(header == null ? "" : header);
            bodies.add(bodyAsString(exchange));
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void samplingQueryIsACompileTimeConstantAndIsSentWithVariables() throws Exception {
        assertTrue(SamplingApiService.QUERY.contains("query GetSamplingConfig"));

        GraphQLClient client = new GraphQLClient(url("/graphql"));
        SamplingConfig config = new SamplingApiService(client).getSamplingConfig("sdk-test");

        assertNotNull(config, "sampling config should parse");
        assertEquals(1, bodies.size());
        assertTrue(bodies.get(0).contains("query GetSamplingConfig"), "query text was not sent");
        assertTrue(bodies.get(0).contains("\"organization_verbose_id\":\"sdk-test\""), "variables were not sent");
        assertEquals("", headerValues.get(0), "no custom header was configured");
    }

    @Test
    void customHeadersAreSentWithEveryRequest() throws Exception {
        GraphQLClient client = new GraphQLClient(url("/graphql"), Map.of("X-Test-Header", "abc"), null);

        client.execute(SamplingApiService.QUERY, Map.of("organization_verbose_id", "sdk-test"));
        client.execute(SamplingApiService.QUERY, Map.of("organization_verbose_id", "sdk-test"));

        assertEquals(List.of("abc", "abc"), headerValues);
    }

    @Test
    void proxySelectorRoutesRequestThroughProxy() throws Exception {
        // The local server acts as the HTTP proxy: a proxied plain-HTTP request
        // carries the absolute target URI in its request line.
        InetSocketAddress proxyAddress = server.getAddress();
        GraphQLClient client = new GraphQLClient(
                "http".concat("://backend.invalid/sampling"),
                Collections.emptyMap(),
                new ProxySelectorForTest(proxyAddress));

        JsonElement data = client.execute(SamplingApiService.QUERY, Map.of("organization_verbose_id", "sdk-test"));

        assertNotNull(data, "the proxied request should succeed");
        assertEquals(1, requestLines.size());
        assertTrue(requestLines.get(0).contains("backend.invalid/sampling"),
                "the proxy did not receive the absolute target URI: " + requestLines.get(0));
    }

    @Test
    void nonSuccessStatusReturnsNull() throws Exception {
        status = 503;
        response = "unavailable";
        GraphQLClient client = new GraphQLClient(url("/graphql"));

        assertNull(client.execute(SamplingApiService.QUERY, Map.of("organization_verbose_id", "sdk-test")));
        assertFalse(bodies.isEmpty());
    }

    private String url(String path) {
        return "http".concat("://") + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + path;
    }

    private static String bodyAsString(HttpExchange exchange) throws IOException {
        byte[] raw = exchange.getRequestBody().readAllBytes();
        String encoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
        if (encoding != null && encoding.toLowerCase().contains("gzip")) {
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                raw = in.readAllBytes();
            }
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    /** Sends everything through one HTTP proxy. */
    static final class ProxySelectorForTest extends java.net.ProxySelector {
        private final List<java.net.Proxy> proxies;

        ProxySelectorForTest(InetSocketAddress address) {
            this.proxies = List.of(new java.net.Proxy(java.net.Proxy.Type.HTTP, address));
        }

        @Override
        public List<java.net.Proxy> select(java.net.URI uri) {
            return proxies;
        }

        @Override
        public void connectFailed(java.net.URI uri, java.net.SocketAddress sa, IOException ioe) {
        }
    }
}
