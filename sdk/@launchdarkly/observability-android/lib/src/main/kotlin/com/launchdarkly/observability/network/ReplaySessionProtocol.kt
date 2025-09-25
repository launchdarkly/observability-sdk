package com.launchdarkly.observability.network

import com.launchdarkly.observability.replay.SessionInitializationEntity
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * GraphQL response models for initialize replay session
 */
@Serializable
data class InitializeReplaySessionResponse(
    val initializeSession: InitializeSessionResponse?
)

/**
 * GraphQL response model for identify session mutation
 */
@Serializable
data class IdentifySessionResponse(
    val identifySession: String? = null
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
@Serializable(with = EventTypeSerializer::class)
enum class EventType(val value: Int) {
    DOM_CONTENT_LOADED(0),
    LOAD(1),
    FULL_SNAPSHOT(2),
    INCREMENTAL_SNAPSHOT(3),
    META(4),
    CUSTOM(5),
    PLUGIN(6)
}

object EventTypeSerializer : KSerializer<EventType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("EventType", PrimitiveKind.INT)
    
    override fun serialize(encoder: Encoder, value: EventType) {
        encoder.encodeInt(value.value)
    }
    
    override fun deserialize(decoder: Decoder): EventType {
        val intValue = decoder.decodeInt()
        return EventType.values().find { it.value == intValue }
            ?: throw IllegalArgumentException("Unknown EventType value: $intValue")
    }
}

/**
 * Node types for DOM elements
 */
@Serializable(with = NodeTypeSerializer::class)
enum class NodeType(val value: Int) {
    DOCUMENT(0),
    DOCUMENT_TYPE(1),
    ELEMENT(2),
    TEXT(3),
    CDATA(4),
    COMMENT(5)
}

object NodeTypeSerializer : KSerializer<NodeType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NodeType", PrimitiveKind.INT)
    
    override fun serialize(encoder: Encoder, value: NodeType) {
        encoder.encodeInt(value.value)
    }
    
    override fun deserialize(decoder: Decoder): NodeType {
        val intValue = decoder.decodeInt()
        return NodeType.values().find { it.value == intValue }
            ?: throw IllegalArgumentException("Unknown NodeType value: $intValue")
    }
}

/**
 * Incremental source types for replay events
 */
@Serializable(with = IncrementalSourceSerializer::class)
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

object IncrementalSourceSerializer : KSerializer<IncrementalSource> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IncrementalSource", PrimitiveKind.INT)
    
    override fun serialize(encoder: Encoder, value: IncrementalSource) {
        encoder.encodeInt(value.value)
    }
    
    override fun deserialize(decoder: Decoder): IncrementalSource {
        val intValue = decoder.decodeInt()
        return IncrementalSource.values().find { it.value == intValue }
            ?: throw IllegalArgumentException("Unknown IncrementalSource value: $intValue")
    }
}

/**
 * Mouse interaction types
 */
@Serializable(with = MouseInteractionsSerializer::class)
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

object MouseInteractionsSerializer : KSerializer<MouseInteractions> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MouseInteractions", PrimitiveKind.INT)
    
    override fun serialize(encoder: Encoder, value: MouseInteractions) {
        encoder.encodeInt(value.value)
    }
    
    override fun deserialize(decoder: Decoder): MouseInteractions {
        val intValue = decoder.decodeInt()
        return MouseInteractions.values().find { it.value == intValue }
            ?: throw IllegalArgumentException("Unknown MouseInteractions value: $intValue")
    }
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
)

/**
 * Custom event data structure for replay events
 */
@Serializable
data class CustomEventData(
    val tag: String? = null,
    val payload: JsonElement? = null
)

/**
 * Custom serializer for EventDataUnion that flattens the data structure
 */
object EventDataUnionSerializer : KSerializer<EventDataUnion> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EventDataUnion")
    
    override fun serialize(encoder: Encoder, value: EventDataUnion) {
        when (value) {
            is EventDataUnion.StandardEventData -> {
                encoder.encodeSerializableValue(EventData.serializer(), value.data)
            }
            is EventDataUnion.CustomEventDataWrapper -> {
                encoder.encodeSerializableValue(JsonElement.serializer(), value.data)
            }
        }
    }
    
    override fun deserialize(decoder: Decoder): EventDataUnion {
        // For deserialization, we need to determine the type based on the content
        // This is a simplified implementation - in practice, you might need more sophisticated logic
        // to determine whether the data should be StandardEventData or CustomEventDataWrapper
        val jsonElement = decoder.decodeSerializableValue(JsonElement.serializer())
        
        // Try to deserialize as StandardEventData first
        return try {
            val eventData = Json.decodeFromJsonElement(EventData.serializer(), jsonElement)
            EventDataUnion.StandardEventData(eventData)
        } catch (e: Exception) {
            // If that fails, treat as CustomEventDataWrapper with JsonElement
            EventDataUnion.CustomEventDataWrapper(jsonElement)
        }
    }
}

/**
 * Sealed class to represent different types of event data
 */
@Serializable(with = EventDataUnionSerializer::class)
sealed class EventDataUnion {
    @Serializable
    data class StandardEventData(val data: EventData) : EventDataUnion()
    
    @Serializable
    data class CustomEventDataWrapper(val data: JsonElement) : EventDataUnion()
}

/**
 * Event structure for replay session events
 */
@Serializable
data class Event(
    val type: EventType,
    val data: EventDataUnion,
    val timestamp: Long? = null,
    val _sid: Int
)

/**
 * Input structure for replay events in GraphQL mutation
 */
@Serializable
data class ReplayEventsInput(
    val events: List<Event>
)

/**
 * Input structure for error objects in GraphQL mutation
 */
@Serializable
data class ErrorObjectInput(
    val message: String? = null,
    val stack: String? = null,
    val timestamp: Long? = null
)

/**
 * GraphQL response model for push payload mutation
 */
@Serializable
data class PushPayloadResponse(
    val pushPayload: Int? = null
)

