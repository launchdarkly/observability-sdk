package com.launchdarkly.observability.internal.replay;

import com.launchdarkly.sdk.LDValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.GZIPInputStream;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The replay backend's operations, against a fake backend. */
public class ReplayApiTest {
  private final MockWebServer server = new MockWebServer();
  private ReplayApi api;

  @Before
  public void setUp() throws IOException {
    server.start();
    api = new ReplayApi(server.url("/").toString(), Collections.singletonMap("X-Team", "desktop"));
  }

  @After
  public void tearDown() throws IOException {
    api.close();
    server.shutdown();
  }

  static String ok(String operation) {
    return "{\"data\":{\"" + operation + "\":\"x\"}}";
  }

  static LDValue body(RecordedRequest request) throws IOException {
    assertEquals("gzip", request.getHeader("Content-Encoding"));
    try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(request.getBody().readByteArray()))) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      for (int n; (n = in.read(buf)) > 0; ) {
        out.write(buf, 0, n);
      }
      return LDValue.parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
    }
  }

  private ReplayApi.Failure failureOf(MockResponse response) throws InterruptedException {
    server.enqueue(response);
    try {
      api.pushPayload("s", 1, Collections.emptyList());
      fail("expected a failure");
      return null;
    } catch (ReplayApi.Failure e) {
      server.takeRequest();
      return e;
    }
  }

  @Test
  public void initializeSessionSendsWhatAndroidSends() throws Exception {
    server.enqueue(new MockResponse().setBody("{\"data\":{\"initializeSession\":{\"secure_id\":\"s\"}}}"));
    api.initializeSession("mob-key", "abc", "svc", "1.2", "0.1.0");
    RecordedRequest request = server.takeRequest();
    assertEquals("POST", request.getMethod());
    assertEquals("desktop", request.getHeader("X-Team"));
    assertEquals("Java/" + System.getProperty("java.version"), request.getHeader("User-Agent"));
    assertTrue(request.getHeader("Content-Type").startsWith("application/json"));
    LDValue body = body(request);
    assertEquals(ReplayApi.INITIALIZE_SESSION, body.get("query").stringValue());
    LDValue v = body.get("variables");
    assertEquals("abc", v.get("session_secure_id").stringValue());
    assertEquals("mob-key", v.get("organization_verbose_id").stringValue());
    assertEquals("observability-java-client", v.get("client_id").stringValue());
    assertEquals("svc", v.get("serviceName").stringValue());
    assertEquals("1.2", v.get("appVersion").stringValue());
    assertEquals("0.1.0", v.get("clientVersion").stringValue());
    assertEquals("0.1.0", v.get("firstloadVersion").stringValue());
    assertEquals("none", v.get("privacy_setting").stringValue());
    assertFalse(v.get("enable_strict_privacy").booleanValue());
    assertEquals(0, v.get("network_recording_domains").size());
    assertEquals("{}", v.get("clientConfig").stringValue());
    assertEquals("", v.get("id").stringValue());
  }

  @Test
  public void anApplicationUserAgentWins() throws Exception {
    api.close();
    api = new ReplayApi(server.url("/").toString(), Collections.singletonMap("User-Agent", "mine"));
    server.enqueue(new MockResponse().setBody(ok("identifySession")));
    api.identifySession("s", new ReplayItem.Identify(Collections.singletonMap("key", "Jane"), 1, "s"));
    assertEquals("mine", server.takeRequest().getHeader("User-Agent"));
  }

  @Test
  public void identifySessionNamesTheUser() throws Exception {
    server.enqueue(new MockResponse().setBody(ok("identifySession")));
    api.identifySession("abc", new ReplayItem.Identify(Collections.singletonMap("key", "Jane"), 1, "abc"));
    LDValue body = body(server.takeRequest());
    assertEquals(ReplayApi.IDENTIFY_SESSION, body.get("query").stringValue());
    assertEquals("Jane", body.get("variables").get("user_identifier").stringValue());
    assertEquals("Jane", body.get("variables").get("user_object").get("key").stringValue());
  }

  @Test
  public void pushPayloadSendsEventsInTimeOrder() throws Exception {
    server.enqueue(new MockResponse().setBody(ok("pushPayload")));
    api.pushPayload("abc", 7, Arrays.asList(new EventGenerator.Event(20, "{\"n\":2}"),
        new EventGenerator.Event(10, "{\"n\":1}"), new EventGenerator.Event(20, "{\"n\":3}")));
    LDValue v = body(server.takeRequest()).get("variables");
    assertEquals("7", v.get("payload_id").stringValue());
    LDValue events = v.get("events").get("events");
    assertEquals(1, events.get(0).get("n").intValue());
    assertEquals("stable for equal times", 2, events.get(1).get("n").intValue());
    assertEquals(3, events.get(2).get("n").intValue());
    assertEquals("{\"messages\":[]}", v.get("messages").stringValue());
    assertEquals("{\"resources\":[]}", v.get("resources").stringValue());
    assertEquals("{\"webSocketEvents\":[]}", v.get("web_socket_events").stringValue());
    assertEquals(0, v.get("errors").size());
  }

  // -- what can be retried --

  @Test
  public void serverErrorsAndThrottlingAreRecoverable() throws Exception {
    assertTrue(failureOf(new MockResponse().setResponseCode(500)).recoverable);
    assertTrue(failureOf(new MockResponse().setResponseCode(429)).recoverable);
    assertTrue(failureOf(new MockResponse().setResponseCode(408)).recoverable);
    assertTrue(failureOf(new MockResponse().setResponseCode(400)).recoverable);
  }

  @Test
  public void otherClientErrorsAreNot() throws Exception {
    assertFalse(failureOf(new MockResponse().setResponseCode(401)).recoverable);
    assertFalse(failureOf(new MockResponse().setResponseCode(404)).recoverable);
  }

  @Test
  public void aRetryableFlagOnARejectionWins() throws Exception {
    assertTrue(failureOf(new MockResponse().setResponseCode(403)
        .setBody("{\"errors\":[{\"message\":\"m\",\"extensions\":{\"retryable\":true}}]}")).recoverable);
    assertFalse(failureOf(new MockResponse().setResponseCode(503)
        .setBody("{\"errors\":[{\"extensions\":{\"retryable\":false}}]}")).recoverable);
  }

  @Test
  public void graphQlErrorsAreRefusalsUnlessMarkedRetryable() throws Exception {
    ReplayApi.Failure blocked = failureOf(new MockResponse()
        .setBody("{\"errors\":[{\"message\":\"session replay is not available in this region\","
            + "\"extensions\":{\"retryable\":false}}],\"data\":null}"));
    assertFalse(blocked.recoverable);
    assertTrue(blocked.getMessage().contains("not available in this region"));
    assertFalse(failureOf(new MockResponse().setBody("{\"errors\":[{\"message\":\"no\"}]}")).recoverable);
    assertTrue(failureOf(new MockResponse()
        .setBody("{\"errors\":[{\"extensions\":{\"retryable\":true}},{\"extensions\":{}}]}")).recoverable);
  }

  @Test
  public void anyNoMeansNo() {
    assertEquals(Boolean.FALSE, ReplayApi.retryable(LDValue.parse(
        "[{\"extensions\":{\"retryable\":true}},{\"extensions\":{\"retryable\":false}}]")));
    assertNull(ReplayApi.retryable(LDValue.parse("[{\"extensions\":{\"retryable\":\"yes\"}}]")));
    assertNull(ReplayApi.retryable(LDValue.ofNull()));
  }

  @Test
  public void malformedResponsesAreRecoverable() throws Exception {
    assertTrue(failureOf(new MockResponse().setBody("not json")).recoverable);
    assertTrue(failureOf(new MockResponse().setBody("[1]")).recoverable);
    assertTrue(failureOf(new MockResponse().setBody("{\"data\":null}")).recoverable);
  }

  @Test
  public void anUnreachableBackendIsRecoverable() throws Exception {
    server.shutdown();
    try {
      api.pushPayload("s", 1, Collections.emptyList());
      fail("expected a failure");
    } catch (ReplayApi.Failure e) {
      assertTrue(e.recoverable);
      assertTrue(e.getMessage().startsWith("pushPayload failed"));
    }
  }

  @Test
  public void statusesOutsideTheClientRangeAreRecoverable() {
    assertTrue(ReplayApi.httpRecoverable(302));
    assertTrue(ReplayApi.httpRecoverable(502));
    assertFalse(ReplayApi.httpRecoverable(499));
  }
}
