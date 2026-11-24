package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
 * The replay backend's GraphQL operations: starting a session, saying who it belongs to, and pushing
 * its events. The operations and their variables are the Android plugin's, which the backend accepts
 * from any client that names itself in {@code client_id}.
 * <p>
 * Every failure is a {@link Failure} that says whether retrying could help, decided as on Android: a
 * transport error or a 5xx, 400, 408 or 429 can; any other 4xx, or a GraphQL error, cannot, unless the
 * error's {@code extensions.retryable} says otherwise. The backend refuses a session it will not
 * record, such as one from a region where replay is off, with a GraphQL error and
 * {@code retryable: false}.
 */
class ReplayApi {
  static final String CLIENT_ID = "observability-java-client";
  private static final MediaType JSON = MediaType.get("application/json");

  static final String INITIALIZE_SESSION = "mutation initializeSession($session_secure_id: String!"
      + " $organization_verbose_id: String! $enable_strict_privacy: Boolean! $privacy_setting: String!"
      + " $enable_recording_network_contents: Boolean! $clientVersion: String! $firstloadVersion: String!"
      + " $clientConfig: String! $environment: String! $id: String! $appVersion: String $serviceName: String!"
      + " $client_id: String! $network_recording_domains: [String!]) { initializeSession("
      + "session_secure_id: $session_secure_id organization_verbose_id: $organization_verbose_id"
      + " enable_strict_privacy: $enable_strict_privacy enable_recording_network_contents:"
      + " $enable_recording_network_contents clientVersion: $clientVersion firstloadVersion: $firstloadVersion"
      + " clientConfig: $clientConfig environment: $environment appVersion: $appVersion serviceName: $serviceName"
      + " fingerprint: $id client_id: $client_id network_recording_domains: $network_recording_domains"
      + " privacy_setting: $privacy_setting) { secure_id project_id } }";

  static final String IDENTIFY_SESSION = "mutation identifySession($session_secure_id: String!"
      + " $user_identifier: String! $user_object: Any) { identifySession(session_secure_id: $session_secure_id"
      + " user_identifier: $user_identifier user_object: $user_object) }";

  static final String PUSH_PAYLOAD = "mutation PushPayload($session_secure_id: String! $payload_id: ID!"
      + " $events: ReplayEventsInput! $messages: String! $resources: String! $web_socket_events: String!"
      + " $errors: [ErrorObjectInput]!) { pushPayload(session_secure_id: $session_secure_id payload_id: $payload_id"
      + " events: $events messages: $messages resources: $resources web_socket_events: $web_socket_events"
      + " errors: $errors) }";

  /** A failed request, and whether trying it again could succeed. */
  static final class Failure extends IOException {
    private static final long serialVersionUID = 1L;
    final boolean recoverable;

    Failure(String message, boolean recoverable, Throwable cause) {
      super(message, cause);
      this.recoverable = recoverable;
    }
  }

  private final String endpoint;
  private final Map<String, String> headers;
  private final OkHttpClient http;

  /**
   * @param endpoint the backend's GraphQL URL
   * @param headers headers for every request, such as the application's custom ones
   */
  ReplayApi(String endpoint, Map<String, String> headers) {
    this(endpoint, headers, new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build());
  }

  ReplayApi(String endpoint, Map<String, String> headers, OkHttpClient http) {
    this.endpoint = endpoint;
    Map<String, String> all = new LinkedHashMap<>(headers);
    // The backend shows a session's browser from this, so the session reads as the Java version.
    all.putIfAbsent("User-Agent", "Java/" + System.getProperty("java.version"));
    this.headers = Collections.unmodifiableMap(all);
    this.http = http;
  }

  /** Starts a session on the backend, which must happen before anything is pushed to it. */
  void initializeSession(String credential, String sessionId, String serviceName, String serviceVersion,
      String clientVersion) throws Failure {
    execute("initializeSession", INITIALIZE_SESSION, new JsonWriter().beginObject()
        .name("session_secure_id").value(sessionId)
        .name("organization_verbose_id").value(credential)
        .name("enable_strict_privacy").value(false)
        .name("enable_recording_network_contents").value(false)
        .name("clientVersion").value(clientVersion)
        .name("firstloadVersion").value(clientVersion)
        .name("clientConfig").value("{}")
        .name("environment").value("")
        .name("appVersion").value(serviceVersion)
        .name("serviceName").value(serviceName)
        .name("client_id").value(CLIENT_ID)
        .name("network_recording_domains").beginArray().endArray()
        // Masking happens on the device, before anything is sent, so the backend has none to do.
        .name("privacy_setting").value("none")
        .name("id").value("")
        .endObject().toString());
  }

  /** Says who a session belongs to. */
  void identifySession(String sessionId, ReplayItem.Identify identify) throws Failure {
    execute("identifySession", IDENTIFY_SESSION, new JsonWriter().beginObject()
        .name("session_secure_id").value(sessionId)
        .name("user_identifier").value(identify.userIdentifier())
        .name("user_object").stringMap(identify.attributes)
        .endObject().toString());
  }

  /** Pushes events to a session, sorted by time as the backend expects. */
  void pushPayload(String sessionId, int payloadId, List<EventGenerator.Event> events) throws Failure {
    List<EventGenerator.Event> sorted = new java.util.ArrayList<>(events);
    sorted.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
    long size = 0;
    for (EventGenerator.Event event : sorted) {
      size += event.json.length() + 1;
    }
    JsonWriter variables = new JsonWriter((int) Math.min(Integer.MAX_VALUE - 4096L, size + 512)).beginObject()
        .name("session_secure_id").value(sessionId)
        .name("payload_id").value(Integer.toString(payloadId))
        .name("events").beginObject().name("events").beginArray();
    for (EventGenerator.Event event : sorted) {
      variables.raw(event.json);
    }
    variables.endArray().endObject()
        .name("messages").value("{\"messages\":[]}")
        .name("resources").value("{\"resources\":[]}")
        .name("web_socket_events").value("{\"webSocketEvents\":[]}")
        .name("errors").beginArray().endArray()
        .endObject();
    execute("pushPayload", PUSH_PAYLOAD, variables.toString());
  }

  /** Releases the client's connections and threads. */
  void close() {
    http.dispatcher().executorService().shutdown();
    http.connectionPool().evictAll();
  }

  private void execute(String operation, String query, String variablesJson) throws Failure {
    byte[] body = gzip(new JsonWriter(variablesJson.length() + query.length() + 64).beginObject()
        .name("query").value(query)
        .name("variables").raw(variablesJson)
        .endObject().toString().getBytes(StandardCharsets.UTF_8));
    Request.Builder request = new Request.Builder()
        .url(endpoint)
        .header("Content-Encoding", "gzip")
        .post(RequestBody.create(body, JSON));
    headers.forEach(request::header);

    int status;
    String text;
    try (Response response = http.newCall(request.build()).execute()) {
      status = response.code();
      // A response from execute() always has a body.
      ResponseBody responseBody = response.body();
      text = responseBody.string();
    } catch (IOException e) {
      throw new Failure(operation + " failed: " + e, true, e);
    }

    LDValue envelope = parse(text);
    Boolean retryable = retryable(envelope.get("errors"));
    if (status != 200) {
      throw new Failure(operation + " failed with status " + status,
          retryable != null ? retryable : httpRecoverable(status), null);
    }
    if (envelope.getType() != LDValueType.OBJECT) {
      throw new Failure(operation + " returned a response that is not JSON", true, null);
    }
    LDValue errors = envelope.get("errors");
    if (errors.size() > 0) {
      throw new Failure(operation + " was refused: " + errors.toJsonString(), retryable != null && retryable, null);
    }
    if (envelope.get("data").getType() != LDValueType.OBJECT) {
      throw new Failure(operation + " returned no data", true, null);
    }
  }

  private static LDValue parse(String text) {
    try {
      return LDValue.parse(text);
    } catch (RuntimeException e) {
      return LDValue.ofNull();
    }
  }

  /** Whether a 4xx or 5xx status could be temporary. */
  static boolean httpRecoverable(int status) {
    return status < 400 || status > 499 || status == 400 || status == 408 || status == 429;
  }

  /** What the errors say about retrying: false if any says no, true if any says yes, else null. */
  static Boolean retryable(LDValue errors) {
    Boolean verdict = null;
    for (LDValue error : errors.values()) {
      LDValue flag = error.get("extensions").get("retryable");
      if (flag.getType() == LDValueType.BOOLEAN) {
        if (!flag.booleanValue()) {
          return false;
        }
        verdict = true;
      }
    }
    return verdict;
  }

  private static byte[] gzip(byte[] data) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(64, data.length / 4));
    try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
      gzip.write(data);
    } catch (IOException e) {
      throw new IllegalStateException(e); // writing to memory does not fail
    }
    return bytes.toByteArray();
  }
}
