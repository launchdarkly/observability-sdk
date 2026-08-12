package com.launchdarkly.observability.client

import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Turns raw touches into `click` spans.
 *
 * A tap is an ACTION_DOWN followed by an ACTION_UP on the watched pointer within the long-press
 * timeout and touch slop. Detection lives in the full product; the span is emitted through
 * [ObservabilityRuntime.recordClickSpan], the same emitter the manual `trackClick` API uses.
 */
internal fun startTapInstrumentation(
    scope: CoroutineScope,
    interactions: UserInteractionManaging,
    runtime: ObservabilityRuntime,
) {
    scope.launch {
        var downX = 0f
        var downY = 0f
        var downTimeMs = 0L
        // Target description and active screen are captured at ACTION_DOWN (on the main thread,
        // before app handlers run) and described on the span at ACTION_UP. Reading the screen from
        // the sample - not from the live screen stack here on this background collector - avoids
        // stamping the click with a destination screen when the tap navigates.
        var downTargetClassName: String? = null
        var downTargetText: String? = null
        var downTargetResourceId: String? = null
        var downScreenId: String? = null
        var downScreenName: String? = null
        interactions.touchFlow.collect { sample ->
            when (sample.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = sample.x
                    downY = sample.y
                    downTimeMs = sample.timestamp
                    downTargetClassName = sample.targetClassName
                    downTargetText = sample.targetText
                    downTargetResourceId = sample.targetResourceId
                    downScreenId = sample.screenId
                    downScreenName = sample.screenName
                }
                MotionEvent.ACTION_UP -> {
                    if (!runtime.options.analytics.taps) return@collect
                    if (!runtime.options.tracesApi.includeSpans) return@collect
                    val dx = sample.x - downX
                    val dy = sample.y - downY
                    val movedTooFar = dx * dx + dy * dy > TAP_SLOP_SQUARED_PX
                    val duration = sample.timestamp - downTimeMs
                    if (movedTooFar || duration > TAP_TIMEOUT_MS) return@collect

                    // Per analytics-taxonomy §4.1 `click`: one event for all element types,
                    // described through the `event.*` namespace. `event.tag` is the short element
                    // tag (e.g. `Button`); the fully-qualified class name is kept in
                    // `event.classname`. `event.screen_id`/`event.screen_name` correlate the tap
                    // with the screen it landed on, captured at ACTION_DOWN.
                    val attrs = ClickAttributes.build(
                        tag = downTargetClassName?.let { shortElementTag(it) },
                        classname = downTargetClassName,
                        id = downTargetResourceId,
                        text = downTargetText,
                        screenId = downScreenId,
                        screenName = downScreenName,
                        x = sample.x.toLong(),
                        y = sample.y.toLong(),
                    )
                    runtime.recordClickSpan(
                        attributes = attrs,
                        startTimeMs = downTimeMs,
                        endTimeMs = sample.timestamp,
                    )
                }
            }
        }
    }
}

/**
 * Derives the short element tag (`event.tag`) from a fully-qualified view class name, keeping click
 * analytics aligned with the cross-platform taxonomy (e.g. `android.widget.Button` -> `Button`).
 * Trailing package and nested-class prefixes are dropped; the original string is returned if no
 * short form can be derived.
 */
internal fun shortElementTag(className: String): String =
    className.substringAfterLast('.').substringAfterLast('$').ifEmpty { className }

// Tap detection thresholds. Long-press timeout separates taps from long presses; the slop (12px,
// matching the Session Replay move filter) separates taps from drags.
private val TAP_TIMEOUT_MS = ViewConfiguration.getLongPressTimeout().toLong()
private const val TAP_SLOP_SQUARED_PX = 144 // 12 x 12 px
