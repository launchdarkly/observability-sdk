package com.launchdarkly.observability.replay

import com.launchdarkly.observability.coroutines.DispatcherProviderHolder
import com.launchdarkly.observability.network.GraphQLClient
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val REPLAY_EXPORTER_NAME = "RRwebGraphQLReplayLogExporter"

/**
 * An [LogRecordExporter] that can send session replay capture logs to the backend using RRWeb syntax
 * and GraphQL pushes for transport.
 *
 * @param organizationVerboseId the organization verbose id for the LaunchDarkly customer
 * @param backendUrl The backend URL the GraphQL operations
 * @param serviceName The service name
 * @param serviceVersion The service version
 * @param injectedReplayApiService Optional SessionReplayApiService for testing. If null, a default service will be created.
 */
class RRwebGraphQLReplayLogExporter(
    val organizationVerboseId: String,
    val backendUrl: String,
    val serviceName: String,
    val serviceVersion: String,
    private val injectedReplayApiService: SessionReplayApiService? = null
) : LogRecordExporter {
    private val coroutineScope = CoroutineScope(DispatcherProviderHolder.current.io + SupervisorJob())

    private var graphqlClient: GraphQLClient = GraphQLClient(backendUrl)
    private val replayApiService: SessionReplayApiService = injectedReplayApiService ?: SessionReplayApiService(
        graphqlClient = graphqlClient,
        serviceName = serviceName,
        serviceVersion = serviceVersion,
    )

    // TODO: O11Y-624 - need to implement sid, payloadId reset when multiple sessions occur in one application process lifecycle.
    private var sidCounter = 0
    private var payloadIdCounter = 0

    private data class LastSentState(
        val sessionId: String?,
        val height: Int,
        val width: Int,
    )

    private var lastSentState = LastSentState(sessionId = null, height = 0, width = 0)

    override fun export(logs: MutableCollection<LogRecordData>): CompletableResultCode {
        val resultCode = CompletableResultCode()

        coroutineScope.launch {
            try {
                for (log in logs) {
                    val capture = extractCaptureFromLog(log)
                    if (capture != null) {
                        // TODO: O11Y-624 - investigate if there is a size limit on the push that is imposed server side.
                        val success =
                            if (capture.session != lastSentState.sessionId || capture.origHeight != lastSentState.height || capture.origWidth != lastSentState.width) {
                                // we need to send a full capture if the session id changes or there is a resize/orientation change
                                sendCaptureFull(capture)
                            } else {
                                sendCaptureIncremental(capture)
                            }
                        if (!success) {
                            // Stop processing immediately on first failure
                            resultCode.fail()
                            return@launch
                        }
                    }
                }

                // All captures processed successfully
                resultCode.succeed()
            } catch (e: Exception) {
                // TODO: O11Y-627 - pass in logger to implementation and use here
                // Log.e("RRwebGraphQLReplayLogExporter", "Error during export: ${e.message}", e)
                resultCode.fail()
            }
        }

        return resultCode
    }

    override fun flush(): CompletableResultCode {
        // TODO: O11Y-621 - Handle flush
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        // TODO: O11Y-621 - Handle shutdown
        return CompletableResultCode.ofSuccess()
    }

    fun nextSid(): Int {
        sidCounter++
        return sidCounter
    }

    fun nextPayloadId(): Int {
        payloadIdCounter++
        return payloadIdCounter
    }

    // Returns null if unable to extract a valid capture from the log record
    private fun extractCaptureFromLog(log: LogRecordData): Capture? {
        val attributes = log.attributes
        val eventDomain = attributes.get(AttributeKey.stringKey("event.domain"))
        val imageWidth = attributes.get(AttributeKey.longKey("image.width"))
        val imageHeight = attributes.get(AttributeKey.longKey("image.height"))
        val imageData = attributes.get(AttributeKey.stringKey("image.data"))
        val sessionId = attributes.get(AttributeKey.stringKey("session.id"))

        // Return null if any required attribute is missing
        if (eventDomain != "media" || imageWidth == null || imageHeight == null || imageData == null || sessionId == null) {
            return null
        }

        return Capture(
            imageBase64 = imageData,
            origHeight = imageHeight.toInt(),
            origWidth = imageWidth.toInt(),
            timestamp = log.observedTimestampEpochNanos / 1_000_000, // Convert nanoseconds to milliseconds
            session = sessionId
        )
    }

    /**
     * Sends an incremental capture. Used after [sendCaptureFull] has already been called for a previous capture in the same session.
     *
     * @param capture the capture to be sent
     */
    suspend fun sendCaptureIncremental(capture: Capture): Boolean = withContext(DispatcherProviderHolder.current.io) {
        try {
            val eventsBatch = mutableListOf<Event>()
            val timestamp = System.currentTimeMillis()

            // TODO: O11Y-625 - optimize JSON usage for performance since this region of code is essentially static
            val incrementalEvent = Event(
                type = EventType.INCREMENTAL_SNAPSHOT,
                timestamp = timestamp,
                sid = nextSid(),
                data = EventDataUnion.CustomEventDataWrapper(
                    Json.parseToJsonElement("""{"source":9,"id":6,"type":0,"commands":[{"property":"clearRect","args":[0,0,${capture.origWidth},${capture.origHeight}]},{"property":"drawImage","args":[{"rr_type":"ImageBitmap","args":[{"rr_type":"Blob","data":[{"rr_type":"ArrayBuffer","base64":"${capture.imageBase64}"}],"type":"image/jpeg"}]},0,0,${capture.origWidth},${capture.origHeight}]}]}""")
                )
            )
            eventsBatch.add(incrementalEvent)

            // TODO: O11Y-629 - remove this spoofed mouse interaction when proper user interaction is instrumented
            // This spoofed mouse interaction is necessary to make the session look like it had activity
            eventsBatch.add(
                Event(
                    type = EventType.INCREMENTAL_SNAPSHOT,
                    timestamp = timestamp,
                    sid = nextSid(),
                    data = EventDataUnion.CustomEventDataWrapper(
                        Json.parseToJsonElement("""{"source":2,"type":2,"x":1, "y":1}""")
                    )
                )
            )

            replayApiService.pushPayload(capture.session, "${nextPayloadId()}", eventsBatch)
            
            // record last sent state only after successful completion
            lastSentState = LastSentState(sessionId = capture.session, height = capture.origHeight, width = capture.origWidth)

            true
        } catch (e: Exception) {
            // TODO: O11Y-627 - pass in logger to implementation and use here
//            Log.e(
//                REPLAY_EXPORTER_NAME,
//                "Error sending incremental capture for session: ${e.message}",
//                e
//            )
            false
        }
    }

    /**
     * Sends a full capture. May be invoked multiple times for a single session if a substantial
     * change occurs requiring a full capture to be sent.
     *
     * @param capture the capture to be sent
     */
    suspend fun sendCaptureFull(capture: Capture): Boolean = withContext(DispatcherProviderHolder.current.io) {
        try {
            replayApiService.initializeReplaySession(organizationVerboseId, capture.session)
            replayApiService.identifyReplaySession(capture.session)

            val eventBatch = mutableListOf<Event>()

            // TODO: O11Y-625 - optimize JSON usage for performance since this region of code is essentially static

            val timestamp = System.currentTimeMillis()
            val metaEvent = Event(
                type = EventType.META,
                timestamp = timestamp,
                sid = nextSid(),
                data = EventDataUnion.StandardEventData(
                    EventData(
                        width = capture.origWidth,
                        height = capture.origHeight,
                    )
                ),
            )
            eventBatch.add(metaEvent)

            val snapShotEvent = Event(
                type = EventType.FULL_SNAPSHOT,
                timestamp = timestamp,
                sid = nextSid(),
                data = EventDataUnion.StandardEventData(
                    EventData(
                        node = EventNode(
                            id = 1,
                            type = NodeType.DOCUMENT,
                            childNodes = listOf(
                                EventNode(
                                    id = 2,
                                    type = NodeType.DOCUMENT_TYPE,
                                    name = "html",
                                ),
                                EventNode(
                                    id = 3,
                                    type = NodeType.ELEMENT,
                                    tagName = "html",
                                    attributes = mapOf("lang" to "en"),
                                    childNodes = listOf(
                                        EventNode(
                                            id = 4,
                                            type = NodeType.ELEMENT,
                                            tagName = "head",
                                            attributes = emptyMap(),
                                        ),
                                        EventNode(
                                            id = 5,
                                            type = NodeType.ELEMENT,
                                            tagName = "body",
                                            attributes = emptyMap(),
                                            childNodes = listOf(
                                                EventNode(
                                                    id = 6,
                                                    type = NodeType.ELEMENT,
                                                    tagName = "canvas",
                                                    attributes = mapOf(
                                                        "rr_dataURL" to "data:image/jpeg;base64,${capture.imageBase64}",
                                                        "width" to "${capture.origWidth}",
                                                        "height" to "${capture.origHeight}"
                                                    ),
                                                    childNodes = listOf(),
                                                )
                                            )
                                        )
                                    )
                                )
                            ),
                        ),
                    )
                ),
            )
            eventBatch.add(snapShotEvent)

            val viewportEvent = Event(
                type = EventType.CUSTOM,
                timestamp = timestamp,
                sid = nextSid(),
                data = EventDataUnion.CustomEventDataWrapper(
                    Json.parseToJsonElement("""{"tag":"Viewport","payload":{"width":${capture.origWidth},"height":${capture.origHeight},"availWidth":${capture.origWidth},"availHeight":${capture.origHeight},"colorDepth":30,"pixelDepth":30,"orientation":0}}""")
                )
            )
            eventBatch.add(viewportEvent)

            // TODO: O11Y-624 - double check error case handling, may need to add retries per api service request, should subsequent requests wait for previous requests to succeed?
            replayApiService.pushPayload(capture.session, "${nextPayloadId()}", eventBatch)

            // record last sent state only after successful completion
            lastSentState = LastSentState(sessionId = capture.session, height = capture.origHeight, width = capture.origWidth)

            true
        } catch (e: Exception) {
            // TODO: O11Y-627 - pass in logger to implementation and use here
//            Log.e(
//                REPLAY_EXPORTER_NAME,
//                "Error sending initial capture for session: ${e.message}",
//                e
//            )
            false
        }
    }
}
