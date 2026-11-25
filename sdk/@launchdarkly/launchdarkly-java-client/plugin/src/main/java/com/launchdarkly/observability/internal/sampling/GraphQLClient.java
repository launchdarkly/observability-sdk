package com.launchdarkly.observability.internal.sampling;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.ObjectBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A minimal GraphQL client on OkHttp, the HTTP stack the SDK and the OTLP exporters already use.
 * JSON goes through the SDK's {@link LDValue}, so the plugin adds no JSON library.
 */
public final class GraphQLClient {
  private static final MediaType JSON = MediaType.get("application/json");

  private final String endpoint;
  private final Map<String, String> headers;
  private final OkHttpClient httpClient;
  private final LDLogger logger;

  /**
   * @param endpoint the GraphQL endpoint
   * @param headers headers added to every request
   * @param logger where failures are reported
   */
  public GraphQLClient(String endpoint, Map<String, String> headers, LDLogger logger) {
    this.endpoint = endpoint;
    this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    this.logger = logger;
    this.httpClient = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build();
  }

  /**
   * Runs a query and returns the {@code data} object of the response.
   *
   * @param query the GraphQL query
   * @param variables the query variables
   * @return the {@code data} object, or null if the request failed or the response had none
   * @throws IOException if the request could not be sent or the response read
   */
  public LDValue execute(String query, Map<String, String> variables) throws IOException {
    ObjectBuilder vars = LDValue.buildObject();
    for (Map.Entry<String, String> variable : variables.entrySet()) {
      vars.put(variable.getKey(), variable.getValue());
    }
    String body = LDValue.buildObject().put("query", query).put("variables", vars.build()).build().toJsonString();

    Request.Builder request = new Request.Builder()
        .url(endpoint)
        .header("Content-Encoding", "gzip")
        .post(RequestBody.create(gzip(body.getBytes(StandardCharsets.UTF_8)), JSON));
    for (Map.Entry<String, String> header : headers.entrySet()) {
      request.header(header.getKey(), header.getValue());
    }
    try (Response response = httpClient.newCall(request.build()).execute()) {
      if (!response.isSuccessful()) {
        logger.warn("The sampling configuration request failed with status {}", response.code());
        return null;
      }
      ResponseBody responseBody = response.body();
      LDValue envelope = LDValue.parse(responseBody == null ? "" : responseBody.string());
      if (envelope.getType() != LDValueType.OBJECT) {
        logger.warn("The sampling configuration response was not a JSON object");
        return null;
      }
      LDValue errors = envelope.get("errors");
      if (errors.size() > 0) {
        logger.warn("The sampling configuration response had errors: {}", errors.toJsonString());
      }
      LDValue data = envelope.get("data");
      return data.getType() == LDValueType.OBJECT ? data : null;
    }
  }

  /** Releases the client's connections and threads. */
  public void close() {
    httpClient.dispatcher().executorService().shutdown();
    httpClient.connectionPool().evictAll();
  }

  private static byte[] gzip(byte[] data) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
      gzip.write(data);
    }
    return bytes.toByteArray();
  }
}
