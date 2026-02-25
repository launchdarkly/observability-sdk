package com.launchdarkly.LDNative

import android.view.View
import com.launchdarkly.observability.api.ldMask
import com.launchdarkly.observability.api.ldUnmask

/**
 * Kotlin analog of the Swift LDMasking class.
 */
object LDMasking {
    @JvmStatic
    fun mask(view: View) {
        view.ldMask()
    }

    @JvmStatic
    fun unmask(view: View) {
        view.ldUnmask()
    }
}
