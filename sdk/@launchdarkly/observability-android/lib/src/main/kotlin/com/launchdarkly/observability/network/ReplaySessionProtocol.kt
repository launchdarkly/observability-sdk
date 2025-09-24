package com.launchdarkly.observability.network

import com.launchdarkly.observability.replay.SessionInitializationEntity
import kotlinx.serialization.Serializable

/**
 * GraphQL response models for initialize replay session
 */
@Serializable
data class InitializeReplaySessionResponse(
    val initializeSession: InitializeSessionResponse?
)

@Serializable
data class InitializeSessionResponse(
    val secure_id: String? = null,
    val project_id: String? = null,
    val sampling: SamplingConfigResponse? = null
) {
    fun mapToEntity(): SessionInitializationEntity? {
        return SessionInitializationEntity(
            secureId = secure_id,
            projectId = project_id,
            sampling = sampling?.mapToEntity()
        )
    }
}

/**
 * Event types for replay session
 */
@Serializable
enum class EventType(val value: Int) {
    DOM_CONTENT_LOADED(0),
    LOAD(1),
    FULL_SNAPSHOT(2),
    INCREMENTAL_SNAPSHOT(3),
    META(4),
    CUSTOM(5),
    PLUGIN(6)
}

/**
 * Node types for DOM elements
 */
@Serializable
enum class NodeType(val value: Int) {
    DOCUMENT(0),
    DOCUMENT_TYPE(1),
    ELEMENT(2),
    TEXT(3),
    CDATA(4),
    COMMENT(5)
}

/**
 * Incremental source types for replay events
 */
@Serializable
enum class IncrementalSource(val value: Int) {
    MUTATION(0),
    MOUSE_MOVE(1),
    MOUSE_INTERACTION(2),
    SCROLL(3),
    VIEWPORT_RESIZE(4),
    INPUT(5),
    TOUCH_MOVE(6),
    MEDIA_INTERACTION(7),
    STYLE_SHEET_RULE(8),
    CANVAS_MUTATION(9),
    FONT(10),
    LOG(11),
    DRAG(12),
    STYLE_DECLARATION(13),
    SELECTION(14),
    ADOPTED_STYLE_SHEET(15),
    CUSTOM_ELEMENT(16)
}

/**
 * Mouse interaction types
 */
@Serializable
enum class MouseInteractions(val value: Int) {
    MOUSE_UP(0),
    MOUSE_DOWN(1),
    CLICK(2),
    CONTEXT_MENU(3),
    DBL_CLICK(4),
    FOCUS(5),
    BLUR(6),
    TOUCH_START(7),
    TOUCH_MOVE_DEPARTED(8),
    TOUCH_END(9),
    TOUCH_CANCEL(10)
}

/**
 * Event node structure for replay events
 */
@Serializable
data class EventNode(
    val type: NodeType,
    val name: String? = null,
    val tagName: String? = null,
    val attributes: Map<String, String>? = null,
    val childNodes: List<EventNode> = emptyList(),
    val rootId: Int? = null,
    val id: Int? = null
)

/**
 * Attributes structure for replay events
 */
@Serializable
data class Attributes(
    val id: Int? = null,
    val attributes: Map<String, String>? = null
)

/**
 * Removal structure for replay events
 */
@Serializable
data class Removal(
    val parentId: Int,
    val id: Int
)

/**
 * Addition structure for replay events
 */
@Serializable
data class Addition(
    val parentId: Int,
    val nextId: Int? = null,
    val node: EventNode
)

/**
 * Event data structure for replay events
 */
@Serializable
data class EventData(
    val source: IncrementalSource? = null,
    val type: MouseInteractions? = null,
    val texts: List<String>? = null,
    val attributes: List<Attributes>? = null,
    val href: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val node: EventNode? = null,
    val removes: List<Removal>? = null,
    val adds: List<Addition>? = null,
    val id: Int? = null,
    val x: Double? = null,
    val y: Double? = null,
    val tag: String? = null,
    val payload: String? = null,
)

/**
 * Event structure for replay session events
 */
@Serializable
data class Event(
    val type: EventType,
    val data: EventData,
    val timestamp: Long? = null,
    val _sid: Int
)

