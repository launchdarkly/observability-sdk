package com.launchdarkly.observability.replay

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import android.view.View

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
    // Create a traditional Android View that can be detected by the view hierarchy
    AndroidView(
        factory = { context ->
            View(context).apply {
                tag = "ld-sensitive-mask"
                contentDescription = "sensitive"
                // Make the view invisible but still part of the hierarchy
                visibility = View.INVISIBLE
                // Set minimum size to ensure it's detectable
                minimumWidth = 1
                minimumHeight = 1
            }
        },
        modifier = Modifier.testTag("ld-sensitive-mask").semantics {
            contentDescription = "sensitive"
        }
    )
    
    // Render the actual content
    Box {
        content()
    }
}