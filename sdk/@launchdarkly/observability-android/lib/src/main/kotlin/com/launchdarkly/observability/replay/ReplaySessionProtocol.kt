package com.launchdarkly.observability.replay

import com.launchdarkly.observability.network.SamplingConfigResponse
import com.launchdarkly.observability.sampling.SamplingConfig
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass

@Serializable
data class InitializeReplaySessionResponse(
    val initializeSession: InitializeSessionResponse?
)

@Serializable
data class IdentifySessionResponse(
    val identifySession: String? = null
)

data class SessionInitializationEntity(
    val secureId: String?,
    val projectId: String?,
    val sampling: SamplingConfig?
)

@Serializable
data class InitializeSessionResponse(
    @SerialName("secure_id")
    val secureId: String? = null,
    @SerialName("project_id")
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
}

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

object EventTypeSerializer : IntEnumSerializer<EventType>(EventType::class, "EventType", EventType::value)

@Serializable(with = NodeTypeSerializer::class)
enum class NodeType(val value: Int) {
    DOCUMENT(0),
    DOCUMENT_TYPE(1),
    ELEMENT(2),
    TEXT(3),
    CDATA(4),
    COMMENT(5)
}

object NodeTypeSerializer : IntEnumSerializer<NodeType>(NodeType::class, "NodeType", NodeType::value)

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

object IncrementalSourceSerializer : IntEnumSerializer<IncrementalSource>(IncrementalSource::class, "IncrementalSource", IncrementalSource::value)

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

object MouseInteractionsSerializer : IntEnumSerializer<MouseInteractions>(MouseInteractions::class, "MouseInteractions", MouseInteractions::value)

open class IntEnumSerializer<T : Enum<T>>(
    enumClass: KClass<T>,
    private val serialName: String,
    private val valueSelector: (T) -> Int
) : KSerializer<T> {
    private val entries: List<T> = enumClass.java.enumConstants?.toList() ?: emptyList()
    private val lookup: Map<Int, T> = entries.associateBy(valueSelector)

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeInt(valueSelector(value))
    }

    override fun deserialize(decoder: Decoder): T {
        val intValue = decoder.decodeInt()
        // TODO: O11Y-624 - determine better error handling
        return lookup[intValue]
            ?: throw IllegalArgumentException("Unknown $serialName value: $intValue")
    }
}

@Serializable
data class EventNode(
    val type: NodeType,
    val name: String? = null,
    val tagName: String? = null,
    val attributes: Map<String, String>? = null,
    // This EncodeDefault is needed as a workaround, rrweb replay is expecting childNodes to be present even when empty list
    @EncodeDefault val childNodes: List<EventNode> = emptyList(),
    val rootId: Int? = null,
    val id: Int? = null
)

@Serializable
data class Attributes(
    val id: Int? = null,
    val attributes: Map<String, String>? = null
)

@Serializable
data class Removal(
    val parentId: Int,
    val id: Int
)

@Serializable
data class Addition(
    val parentId: Int,
    val nextId: Int? = null,
    val node: EventNode
)

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

@Serializable(with = EventDataUnionSerializer::class)
sealed class EventDataUnion {
    @Serializable
    data class StandardEventData(val data: EventData) : EventDataUnion()

    @Serializable
    data class CustomEventDataWrapper(val data: JsonElement) : EventDataUnion()
}

@Serializable
data class Event(
    val type: EventType,
    val data: EventDataUnion,
    val timestamp: Long,
    @SerialName("_sid")
    val sid: Int
)

@Serializable
data class ReplayEventsInput(
    val events: List<Event>
)

@Serializable
data class ErrorObjectInput(
    val message: String? = null,
    val stack: String? = null,
    val timestamp: Long? = null
)

@Serializable
data class PushPayloadResponse(
    val pushPayload: Int? = null
)
