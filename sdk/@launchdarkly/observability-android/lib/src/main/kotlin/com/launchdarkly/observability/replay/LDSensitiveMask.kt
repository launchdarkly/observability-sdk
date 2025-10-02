package com.launchdarkly.observability.replay

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color


/**
 * A simple composable wrapper view used for masking sensitive content in replay sessions.
 * This view always draws a rectangle on top of the content to mask it.
 *
 * @param content The content to be wrapped and masked as sensitive
 */
@Composable
fun LDSensitiveMask(
    content: @Composable () -> Unit
) {
    // Always apply masking by drawing a rectangle on top of content
    Box(
        modifier = Modifier.applyRedact(Color.Black)
    ) {
        content()
    }
}

/**
 * Modifier that applies masking by drawing a solid color rectangle over the content
 */
fun Modifier.applyRedact(color: Color = Color.Black) =
    drawWithContent {
        drawContent() // Draw the content of the Composable
        drawRect(color) // Draw a solid color rectangle over it
    }

/**
 * Modifier that applies masking by drawing a solid color rectangle over the content
 */
fun Modifier.sensitive(color: Color = Color.Black) =
    drawWithContent {
        drawContent() // Draw the content of the Composable
        drawRect(color) // Draw a solid color rectangle over it
    }