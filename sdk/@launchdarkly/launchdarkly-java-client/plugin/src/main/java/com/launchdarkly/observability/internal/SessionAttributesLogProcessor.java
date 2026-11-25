package com.launchdarkly.observability.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;

/**
 * Adds the session id and the {@link GlobalAttributes} to every log record, filling only keys the
 * record lacks; the session id always wins. Registered first, so filtering, sampling and export see
 * both.
 */
final class SessionAttributesLogProcessor implements LogRecordProcessor {
  private static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey(Constants.SESSION_ID_ATTRIBUTE);

  private final SessionManager sessions;

  SessionAttributesLogProcessor(SessionManager sessions) {
    this.sessions = sessions;
  }

  @Override
  public void onEmit(Context context, ReadWriteLogRecord logRecord) {
    Attributes globals = GlobalAttributes.get();
    if (!globals.isEmpty()) {
      Attributes missing = GlobalAttributes.missingFrom(globals, logRecord.toLogRecordData().getAttributes());
      if (!missing.isEmpty()) {
        logRecord.setAllAttributes(missing);
      }
    }
    logRecord.setAttribute(SESSION_ID, sessions.getSessionId());
  }
}
