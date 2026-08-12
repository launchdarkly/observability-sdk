package com.launchdarkly.observability.bridge

/** Attribute keys used to carry bridge-supplied IDs through the
 *  OTel pipeline so the exporter can override the auto-generated IDs. */
const val BRIDGE_TRACE_ID_ATTRIBUTE_KEY = "__bridge.trace_id"
const val BRIDGE_SPAN_ID_ATTRIBUTE_KEY = "__bridge.span_id"
