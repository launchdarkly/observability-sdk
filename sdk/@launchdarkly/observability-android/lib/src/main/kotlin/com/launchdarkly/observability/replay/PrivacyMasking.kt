package com.launchdarkly.observability.replay

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

internal class PrivacyMaskRegistry {
    private val _maskRects = MutableStateFlow<Map<String, Rect>>(emptyMap())
    val maskRects: StateFlow<Map<String, Rect>> = _maskRects.asStateFlow()

    fun upsert(id: String, rect: Rect) {
        _maskRects.update { current -> current + (id to rect) }
    }

    fun remove(id: String) {
        _maskRects.update { current -> current - id }
    }

    fun snapshot(): List<Rect> = _maskRects.value.values.toList()
}

internal object PrivacyMasking {
    private val _registry = MutableStateFlow<PrivacyMaskRegistry?>(null)
    val registry: StateFlow<PrivacyMaskRegistry?> = _registry.asStateFlow()

    fun register(registry: PrivacyMaskRegistry) {
        _registry.value = registry
    }

    fun unregister(registry: PrivacyMaskRegistry) {
        _registry.update { current ->
            if (current === registry) null else current
        }
    }
}

fun Modifier.maskSensitive(): Modifier = composed {
    val activeRegistry by PrivacyMasking.registry.collectAsState(initial = null)

    if (activeRegistry == null) {
        return@composed this
    }

    val maskId = remember { UUID.randomUUID().toString() }

    DisposableEffect(activeRegistry, maskId) {
        onDispose { activeRegistry?.remove(maskId) }
    }

    this.then(
        Modifier.onGloballyPositioned { coords ->
            activeRegistry?.upsert(maskId, coords.boundsInWindow())
        }
    )
}
