package com.example.androidobservability

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.launchdarkly.observability.sdk.LDObserve

/**
 * Repro for "global attributes (e.g. UTM params) on all telemetry".
 *
 * [applyGlobalAttributes] runs at app startup before the observability SDK has finished
 * initializing, so every span, log, and metric recorded afterwards should carry
 * [GLOBAL_ATTRIBUTES]. The existing "Send custom span" and "Trigger Log" buttons print
 * the expectation for the row they produce via [note].
 */
object GlobalAttributesRepro {
    private const val TAG = "GlobalAttributesRepro"

    val GLOBAL_ATTRIBUTES: Map<String, Any?> = mapOf(
        "utm_campaign" to "spring_promo",
        "utm_source" to "email",
    )

    const val EXPECT_GLOBAL = "[#1] expect utm_campaign=spring_promo on this row"
    const val EXPECT_PER_CALL_WINS = "[#1] expect utm_source=per_call (per-call wins over global)"

    fun applyGlobalAttributes() {
        LDObserve.setAttributes(GLOBAL_ATTRIBUTES)
        Log.i(TAG, "[#1] applied global attributes before SDK init: $GLOBAL_ATTRIBUTES")
    }

    fun note(context: Context, action: String, vararg expectations: String) {
        expectations.forEach { Log.i(TAG, "$action: $it") }
        Toast.makeText(context, expectations.joinToString("\n"), Toast.LENGTH_LONG).show()
    }
}
