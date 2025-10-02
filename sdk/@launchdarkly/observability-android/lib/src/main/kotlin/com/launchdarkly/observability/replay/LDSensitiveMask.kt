package com.launchdarkly.observability.replay

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * A simple composable wrapper view used for tagging sensitive content in replay sessions.
 * This view serves as a marker to identify areas that should be masked or hidden during replay.
 *
 * @param content The content to be wrapped and tagged as sensitive
 */
@Composable
fun LDSensitiveMask(
    content: @Composable () -> Unit
) {
    // Render the content with sensitive mask tagging
    Box(
        modifier = Modifier
            .testTag("ld-sensitive-mask")
            .semantics {
                contentDescription = "sensitive"
            }
    ) {
        content()
    }
}