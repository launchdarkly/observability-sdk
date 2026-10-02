package com.launchdarkly.observability.replay

import com.launchdarkly.observability.network.SamplingConfigResponse
import com.launchdarkly.observability.network.intOrNull
import com.launchdarkly.observability.network.objectOrNull
import com.launchdarkly.observability.network.stringOrNull
import com.launchdarkly.observability.sampling.SamplingConfig
import org.json.JSONObject

data class InitializeReplaySessionResponse(
    val initializeSession: InitializeSessionResponse?
) {
    internal companion object {
        fun fromJson(json: JSONObject) = InitializeReplaySessionResponse(
            initializeSession = json.objectOrNull("initializeSession", InitializeSessionResponse::fromJson)
        )
    }
}

data class IdentifySessionResponse(
    val identifySession: String? = null
) {
    internal companion object {
        fun fromJson(json: JSONObject) = IdentifySessionResponse(
            identifySession = json.stringOrNull("identifySession")
        )
    }
}

data class SessionInitializationEntity(
    val secureId: String?,
    val projectId: String?,
    val sampling: SamplingConfig?
)

data class InitializeSessionResponse(
    val secureId: String? = null,
    val projectId: String? = null,
    val sampling: SamplingConfigResponse? = null
) {
    fun mapToEntity(): SessionInitializationEntity? {
        return SessionInitializationEntity(
            secureId = secureId,
            projectId = projectId,
            sampling = sampling?.mapToEntity()
        )
    }

    internal companion object {
        fun fromJson(json: JSONObject) = InitializeSessionResponse(
            secureId = json.stringOrNull("secure_id"),
            projectId = json.stringOrNull("project_id"),
            sampling = json.objectOrNull("sampling", SamplingConfigResponse::fromJson)
        )
    }
}

/** Written as its [value]. */
enum class EventType(val value: Int) {
    DOM_CONTENT_LOADED(0),
    LOAD(1),
    FULL_SNAPSHOT(2),
    INCREMENTAL_SNAPSHOT(3),
    META(4),
    CUSTOM(5),
    PLUGIN(6)
}

/** Written as its [value]. */
enum class NodeType(val value: Int) {
    DOCUMENT(0),
    DOCUMENT_TYPE(1),
    ELEMENT(2),
    TEXT(3),
    CDATA(4),
    COMMENT(5)
}

/** Written as its [value]. */
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

/** Written as its [value]. */
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

data class EventNode(
    val type: NodeType,
    val name: String? = null,
    val tagName: String? = null,
    val attributes: Map<String, String>? = null,
    /** Always written, even when empty: rrweb replay expects every node to carry it. */
    val childNodes: List<EventNode> = emptyList(),
    val rootId: Int? = null,
    val id: Int? = null
)

data class Attributes(
    val id: Int? = null,
    val attributes: Map<String, String>? = null
)

data class Removal(
    val parentId: Int,
    val id: Int
)

data class Addition(
    val parentId: Int,
    val nextId: Int? = null,
    val node: EventNode
)

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
)

sealed class EventDataUnion {
    data class StandardEventData(val data: EventData) : EventDataUnion()

    /**
     * Event data with no fixed shape, written as a JSON object. Values may be `null`, [String], [Boolean],
     * any [Number], or a nested [Map] or [List] of those.
     */
    data class CustomEventDataWrapper(val data: Map<String, Any?>) : EventDataUnion()
}

data class Event(
    val type: EventType,
    val data: EventDataUnion,
    val timestamp: Long,
    /** Written as `_sid`. */
    val sid: Int
)

data class ReplayEventsInput(
    val events: List<Event>
)

data class PushPayloadResponse(
    val pushPayload: Int? = null
) {
    internal companion object {
        fun fromJson(json: JSONObject) = PushPayloadResponse(
            pushPayload = json.intOrNull("pushPayload")
        )
    }
}
