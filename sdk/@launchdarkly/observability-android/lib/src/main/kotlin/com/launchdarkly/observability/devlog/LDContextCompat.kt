package com.launchdarkly.observability.devlog

import com.launchdarkly.sdk.LDContext

/**
 * Converts an [LDContext] to an [LDObserveContext] for use in the Observability SDK.
 *
 * Use this at integration boundaries where code receives an [LDContext] from the
 * LaunchDarkly Client SDK and needs to pass it into observability/session-replay APIs.
 */
fun LDContext.toLDObserveContext(): LDObserveContext {
    if (isMultiple) {
        val subs = (0 until individualContextCount).map { i ->
            getIndividualContext(i).toLDObserveContext()
        }
        return LDObserveContext.createMulti(*subs.toTypedArray())
    }
    return LDObserveContext.create(kind.toString(), key)
}
