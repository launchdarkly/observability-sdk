package com.launchdarkly.observability.replay

import android.util.Log
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

class RRwebGraphQLReplayLogExporter : LogRecordExporter {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var graphqlClient: GraphQLClient
    private lateinit var sdkKey: String
    private lateinit var replayApiService: SessionReplayApiService

    private var sidCounter = 0
    private var payloadIdCounter = 0
    private var lastSentHeight = 0
    private var lastSentWidth = 0
    private var lastSessionId: String? = null

    init {
        graphqlClient = GraphQLClient("https://pub.observability.ld-stg.launchdarkly.com")
        replayApiService = SessionReplayApiService(graphqlClient)

    }

    override fun export(logs: MutableCollection<LogRecordData>): CompletableResultCode {
        val resultCode = CompletableResultCode()

        coroutineScope.launch {
            try {
                var allSuccessful = true
                
                for (log in logs) {
                    val capture = captureFromLog(log)
                    if (capture != null) {
                        val success = if (!capture.session.equals(lastSessionId)) {
                            sendCaptureFull(capture)
                        } else {
                            sendCaptureIncremental(capture)
                        }
                        if (!success) {
                            allSuccessful = false
                        }
                    }
                }
                
                if (allSuccessful) {
                    resultCode.succeed()
                } else {
                    resultCode.fail()
                }
            } catch (e: Exception) {
                Log.e("RRwebGraphQLReplayLogExporter", "Error during export: ${e.message}", e)
                resultCode.fail()
            }
        }
        
        return resultCode
    }

    override fun flush(): CompletableResultCode {
        TODO("Not yet implemented")
    }

    override fun shutdown(): CompletableResultCode {
        TODO("Not yet implemented")
    }

    fun nextSid() : Int {
        sidCounter++;
        return sidCounter
    }

    fun nextPayloadId() : Int {
        payloadIdCounter++;
        return payloadIdCounter
    }

    // Returns null if unable to extract a valid capture from the log record
    private fun captureFromLog(log: LogRecordData) : Capture? {
        val attributes = log.attributes
        
        // Check if all required attributes are present
        val eventDomain = attributes.get(AttributeKey.stringKey("event.domain"))
        val imageWidth = attributes.get(AttributeKey.longKey("image.width"))
        val imageHeight = attributes.get(AttributeKey.longKey("image.height"))
        val imageData = attributes.get(AttributeKey.stringKey("image.data"))
        val sessionId = attributes.get(AttributeKey.stringKey("session.id"))
        
        // Return null if any required attribute is missing
        if (eventDomain == null || imageWidth == null || imageHeight == null || imageData == null || sessionId == null) {
            return null
        }
        
        // Verify that event.domain is "media"
        if (eventDomain != "media") {
            return null
        }
        
        // Extract timestamp from log record
        val timestamp = log.observedTimestampEpochNanos / 1_000_000 // Convert nanoseconds to milliseconds
        
        return Capture(
            imageBase64 = imageData,
            origHeight = imageHeight.toInt(),
            origWidth = imageWidth.toInt(),
            timestamp = timestamp,
            session = sessionId
        )
    }

    suspend fun sendCaptureIncremental(capture: Capture): Boolean = withContext(Dispatchers.IO) {
        try {
            val eventsBatch1 = mutableListOf<Event>()
            val timestamp = System.currentTimeMillis()

            val incrementalEvent = Event(
                type = EventType.INCREMENTAL_SNAPSHOT,
                timestamp = timestamp,
                _sid = nextSid(),
                data = EventDataUnion.CustomEventDataWrapper(
                    Json.parseToJsonElement("""{"source":9,"id":6,"type":0,"commands":[{"property":"clearRect","args":[0,0,${capture.origWidth},${capture.origHeight}]},{"property":"drawImage","args":[{"rr_type":"ImageBitmap","args":[{"rr_type":"Blob","data":[{"rr_type":"ArrayBuffer","base64":"${capture.imageBase64}"}],"type":"image/jpeg"}]},0,0,${capture.origWidth},${capture.origHeight}]}]}""")
                )
            )
            eventsBatch1.add(incrementalEvent)

            replayApiService.pushPayload(sdkKey, capture.session, "${nextPayloadId()}", listOf(incrementalEvent))
            true
        } catch (e: Exception) {
            Log.e("ScreenshotInstrumentation", "Error uploading initing session with screenshot: ${e.message}", e)
            false
        }
    }

    suspend fun sendCaptureFull(capture: Capture): Boolean = withContext(Dispatchers.IO) {
        try {
            val eventsBatch1 = mutableListOf<Event>()
            val eventsBatch2 = mutableListOf<Event>()
            val eventsBatch3 = mutableListOf<Event>()

            val timestamp = System.currentTimeMillis()
            val metaEvent = Event(
                type = EventType.META,
                timestamp = timestamp,
                _sid = nextSid(),
                data = EventDataUnion.StandardEventData(
                    EventData(
                        href = "www.bogus.com", // TODO: see if can remove href
                        width = capture.origWidth,
                        height = capture.origHeight,
                    )
                ),
            )
            eventsBatch1.add(metaEvent)

            val snapShotEvent = Event(
                type = EventType.FULL_SNAPSHOT,
                timestamp = timestamp,
                _sid = nextSid(),
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
            eventsBatch1.add(snapShotEvent)

            val viewportEvent = Event(
                type = EventType.CUSTOM,
                timestamp = timestamp,
                _sid = nextSid(),
                data = EventDataUnion.CustomEventDataWrapper(
                    Json.parseToJsonElement("""{"tag":"Viewport","payload":{"width":${capture.origWidth},"height":${capture.origHeight},"availWidth":${capture.origWidth},"availHeight":${capture.origHeight},"colorDepth":30,"pixelDepth":30,"orientation":0}}""")
                )
            )
            eventsBatch1.add(viewportEvent)

            replayApiService.pushPayload(sdkKey, capture.session, "${nextPayloadId()}", eventsBatch1)

            // mouse interaction 1
            eventsBatch2.add(
                Event(
                    type = EventType.INCREMENTAL_SNAPSHOT,
                    timestamp = timestamp + 2000,
                    _sid = nextSid(),
                    data = EventDataUnion.CustomEventDataWrapper(
                        Json.parseToJsonElement("""{"source":2,"type":2,"x":150, "y":150}""")
                    )
                )
            )

            // mouse interaction 2
            eventsBatch2.add(
                Event(
                    type = EventType.INCREMENTAL_SNAPSHOT,
                    timestamp = timestamp + 5000,
                    _sid = nextSid(),
                    data = EventDataUnion.CustomEventDataWrapper(
                        Json.parseToJsonElement("""{"source":2,"type":2,"x":200, "y":200}""")
                    )
                )
            )

            replayApiService.pushPayload(sdkKey, capture.session, "${nextPayloadId()}", eventsBatch2)

            // mouse interaction 1
            eventsBatch3.add(
                Event(
                    type = EventType.INCREMENTAL_SNAPSHOT,
                    timestamp = timestamp + 6000,
                    _sid = nextSid(),
                    data = EventDataUnion.CustomEventDataWrapper(
                        Json.parseToJsonElement("""{"source":2,"type":2,"x":150, "y":150}""")
                    )
                )
            )

            // mouse interaction 2
            eventsBatch3.add(
                Event(
                    type = EventType.INCREMENTAL_SNAPSHOT,
                    timestamp = timestamp + 7000,
                    _sid = nextSid(),
                    data = EventDataUnion.CustomEventDataWrapper(
                        Json.parseToJsonElement("""{"source":2,"type":2,"x":200, "y":200}""")
                    )
                )
            )
            // Due to a bug backend side, we have to send 3 payloads
            replayApiService.pushPayload(sdkKey, capture.session, "${nextPayloadId()}", eventsBatch3)

            lastSentWidth = capture.origWidth
            lastSentHeight = capture.origHeight

            true
        } catch (e: Exception) {
            // TODO: pass through logger when instrumentation is created or installed.
            Log.e("ScreenshotInstrumentation", "Error uploading initing session with screenshot: ${e.message}", e)
            false
        }
    }
}