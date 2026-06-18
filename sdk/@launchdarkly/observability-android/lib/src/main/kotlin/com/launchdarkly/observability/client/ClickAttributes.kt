package com.launchdarkly.observability.client

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

/**
 * Builds the `event.*` attributes for a `click` span (taxonomy §4.1), shared by the automatic tap
 * instrumentation and the manual [ObservabilityService.trackClick] API.
 *
 * Deliberately kept free of Android-framework dependencies (unlike [ObservabilityService], whose
 * companion touches `ViewConfiguration`) so the click attribute logic stays unit-testable on the
 * plain JVM.
 */
internal object ClickAttributes {
    // `event.*` taxonomy attribute keys for clicks (see analytics-taxonomy.md §4.1).
    val EVENT_TYPE = AttributeKey.stringKey("event.type")
    val EVENT_TAG = AttributeKey.stringKey("event.tag")
    val EVENT_CLASSNAME = AttributeKey.stringKey("event.classname")
    val EVENT_ID = AttributeKey.stringKey("event.id")
    val EVENT_TEXT = AttributeKey.stringKey("event.text")
    val EVENT_SCREEN_ID = AttributeKey.stringKey("event.screen_id")
    val EVENT_SCREEN_NAME = AttributeKey.stringKey("event.screen_name")
    val EVENT_X = AttributeKey.longKey("event.x")
    val EVENT_Y = AttributeKey.longKey("event.y")

    /**
     * Applied in increasing precedence so the taxonomy can never be clobbered: caller [properties]
     * first, then [contextKeyAttributes], then the reserved `event.*` fields last. Optional values
     * are omitted when `null`; `event.type` is always present.
     */
    fun build(
        tag: String?,
        classname: String?,
        id: String?,
        text: String?,
        screenId: String?,
        screenName: String?,
        x: Long?,
        y: Long?,
        contextKeyAttributes: Attributes = Attributes.empty(),
        properties: Attributes = Attributes.empty(),
    ): Attributes {
        val builder = Attributes.builder()
        builder.putAll(properties)
        builder.putAll(contextKeyAttributes)
        builder.put(EVENT_TYPE, UserInteractionManager.CLICK_SPAN_NAME)
        tag?.let { builder.put(EVENT_TAG, it) }
        classname?.let { builder.put(EVENT_CLASSNAME, it) }
        id?.let { builder.put(EVENT_ID, it) }
        text?.let { builder.put(EVENT_TEXT, it) }
        screenId?.let { builder.put(EVENT_SCREEN_ID, it) }
        screenName?.let { builder.put(EVENT_SCREEN_NAME, it) }
        x?.let { builder.put(EVENT_X, it) }
        y?.let { builder.put(EVENT_Y, it) }
        return builder.build()
    }
}
