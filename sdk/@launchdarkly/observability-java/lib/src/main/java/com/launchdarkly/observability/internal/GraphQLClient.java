package com.launchdarkly.observability.internal;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Simple GraphQL client using {@link HttpURLConnection} and Gson.
 */
public final class GraphQLClient {

    private static final Logger log = Logger.getLogger(GraphQLClient.class.getName());
    private static final int CONNECT_TIMEOUT = 10_000;
    private static final int READ_TIMEOUT = 10_000;
    private static final Gson GSON = new Gson();

    private final String endpoint;

    public GraphQLClient(String backendUrl) {
        this.endpoint = backendUrl;
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

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Content-Encoding", "gzip");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                log.warning("GraphQL request failed with status " + status);
                return null;
            }

            String responseBody = readStream(conn.getInputStream());
            JsonObject response = GSON.fromJson(responseBody, JsonObject.class);

            if (response.has("errors") && !response.getAsJsonArray("errors").isEmpty()) {
                log.warning("GraphQL errors: " + response.getAsJsonArray("errors"));
            }

            return response.has("data") ? response.get("data") : null;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Loads a GraphQL query from a classpath resource file.
     */
    public static String loadQuery(String resourcePath) {
        try (InputStream is = GraphQLClient.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return readStream(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load query from " + resourcePath, e);
        }
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
            gzos.write(data);
        }
        return bos.toByteArray();
    }

    private static String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
