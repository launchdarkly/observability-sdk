package com.launchdarkly.observability.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

/**
 * Adds the session id and the {@link GlobalAttributes} current at span start to every span. Global
 * attributes only fill keys the span was not started with; the session id always wins. Registered
 * first, so sampling and export see both.
 */
final class SessionAttributesSpanProcessor implements SpanProcessor {
  private static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey(Constants.SESSION_ID_ATTRIBUTE);

  private final SessionManager sessions;

  SessionAttributesSpanProcessor(SessionManager sessions) {
    this.sessions = sessions;
  }

  @Override
  public void onStart(Context parentContext, ReadWriteSpan span) {
    Attributes globals = GlobalAttributes.get();
    if (!globals.isEmpty()) {
      Attributes missing = GlobalAttributes.missingFrom(globals, span.getAttributes());
      if (!missing.isEmpty()) {
        span.setAllAttributes(missing);
      }
    }
    span.setAttribute(SESSION_ID, sessions.getSessionId());
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(ReadableSpan span) {
  }

  @Override
  public boolean isEndRequired() {
    return false;
  }
}
