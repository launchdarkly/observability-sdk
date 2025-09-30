package com.launchdarkly.observability.replay

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.util.Base64
import android.util.Log
import com.google.auto.service.AutoService
import com.launchdarkly.observability.network.Event
import com.launchdarkly.observability.network.EventData
import com.launchdarkly.observability.network.EventDataUnion
import com.launchdarkly.observability.network.EventNode
import com.launchdarkly.observability.network.EventType
import com.launchdarkly.observability.network.GraphQLClient
import com.launchdarkly.observability.network.NodeType
import com.launchdarkly.observability.network.SessionReplayApiService
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import io.opentelemetry.android.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

@AutoService(AndroidInstrumentation::class)
class ScreenshotInstrumentation : AndroidInstrumentation {
    private lateinit var _screenshotter: ComposeScreenshotter
    private var graphqlClient: GraphQLClient
    private lateinit var replayApiService: SessionReplayApiService
    private lateinit var backendUrl: String
    private lateinit var sdkKey: String
    private lateinit var sessionManager: SessionManager
    private var sidCounter = 0;
    private var payloadIdCounter = 0;
    private var lastSentHeight = 0;
    private var lastSentWidth = 0;

    init {
        sdkKey = "mob-a4328fa9-51e7-4a3d-bb0e-d5ce47464fc6" // TODO: at the moment this is hardcoded to the client side ID for the environment, need to figure this out
//        graphqlClient = GraphQLClient("https://pub.observability.app.launchdarkly.com")
        graphqlClient = GraphQLClient("https://pub.observability.ld-stg.launchdarkly.com")


    }

    override val name: String = "screenshotter"

    override fun install(ctx: InstallationContext) {
        sessionManager = ctx.sessionManager
        _screenshotter = ComposeScreenshotter(sessionManager)
        graphqlClient = GraphQLClient("https://pub.observability.ld-stg.launchdarkly.com")
        replayApiService = SessionReplayApiService(graphqlClient)

        // Initialize replay session in background IO coroutine
        GlobalScope.launch(Dispatchers.IO) {
            val sessionId = sessionManager.getSessionId()
            replayApiService.initializeReplaySession(sdkKey, sessionId)
            replayApiService.identifyReplaySession(sessionId)

            // TODO: this is hacky for development
            while (true) {
                _screenshotter.captureScreenshotNow()
                delay(1000)
            }
        }

        // Hook up screenshot flow to upload routine
        GlobalScope.launch(Dispatchers.IO) {
            var firstScreenshotSent = false;
            _screenshotter.screenshotFlow.collect { screenshot ->
                try {
                    if (!firstScreenshotSent || screenshot.bitmap.width != lastSentWidth || screenshot.bitmap.height != lastSentHeight) {
                        val success = sendFullSnapshot(screenshot)
                        if (success) {
                            firstScreenshotSent = true;
                            Log.d("ScreenshotInstrumentation", "Successfully uploaded first screenshot for session: ${screenshot.session}")
                        } else {
                            Log.w("ScreenshotInstrumentation", "Failed to upload first screenshot for session: ${screenshot.session}")
                        }
                    } else {
                        val success = sendScreenshot(screenshot)
                        if (success) {
                            firstScreenshotSent = true;
                            Log.d("ScreenshotInstrumentation", "Successfully uploaded screenshot for session: ${screenshot.session}")
                        } else {
                            Log.w("ScreenshotInstrumentation", "Failed to upload screenshot for session: ${screenshot.session}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ScreenshotInstrumentation", "Error processing screenshot upload: ${e.message}", e)
                }
            }
        }

        ctx.application.registerActivityLifecycleCallbacks(_screenshotter)
        
    }

    fun nextSid() : Int {
        sidCounter++;
        return sidCounter
    }

    fun nextPayloadId() : Int {
        payloadIdCounter++;
        return payloadIdCounter
    }

    suspend fun sendScreenshot(screenshot: Screenshot): Boolean = withContext(Dispatchers.IO) {
        try {
            val base64Image = encodeBitmapToBase64(screenshot.bitmap)
            val eventsBatch1 = mutableListOf<Event>()
            val timestamp = System.currentTimeMillis()

            val incrementalEvent = Event(
                type = EventType.INCREMENTAL_SNAPSHOT,
                timestamp = timestamp,
                _sid = nextSid(),
                data = EventDataUnion.CustomEventDataWrapper(
                    Json.parseToJsonElement("""{"source":9,"id":6,"type":0,"commands":[{"property":"clearRect","args":[0,0,${screenshot.bitmap.width},${screenshot.bitmap.height}]},{"property":"drawImage","args":[{"rr_type":"ImageBitmap","args":[{"rr_type":"Blob","data":[{"rr_type":"ArrayBuffer","base64":"$base64Image"}],"type":"image/jpeg"}]},0,0,${screenshot.bitmap.width},${screenshot.bitmap.height}]}]}""")
                )
            )
            eventsBatch1.add(incrementalEvent)

            replayApiService.pushPayload(sdkKey, sessionManager.getSessionId(), "${nextPayloadId()}", listOf(incrementalEvent))
            true
        } catch (e: Exception) {
            Log.e("ScreenshotInstrumentation", "Error uploading initing session with screenshot: ${e.message}", e)
            false
        }
    }

    suspend fun sendFullSnapshot(screenshot: Screenshot): Boolean = withContext(Dispatchers.IO) {
        try {
            val base64Image = encodeBitmapToBase64(screenshot.bitmap)
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
                        href = "www.bogus.com",
                        width = screenshot.bitmap.width,
                        height = screenshot.bitmap.height,
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
                                                        "rr_dataURL" to "data:image/jpeg;base64,${base64Image}",
                                                        "width" to "${screenshot.bitmap.width}",
                                                        "height" to "${screenshot.bitmap.height}"
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
                    Json.parseToJsonElement("""{"tag":"Viewport","payload":{"width":${screenshot.bitmap.width},"height":${screenshot.bitmap.height},"availWidth":${screenshot.bitmap.width},"availHeight":${screenshot.bitmap.height},"colorDepth":30,"pixelDepth":30,"orientation":0}}""")
                )
            )
            eventsBatch1.add(viewportEvent)

            replayApiService.pushPayload(sdkKey, sessionManager.getSessionId(), "${nextPayloadId()}", eventsBatch1)

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

            replayApiService.pushPayload(sdkKey, sessionManager.getSessionId(), "${nextPayloadId()}", eventsBatch2)

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
            replayApiService.pushPayload(sdkKey, sessionManager.getSessionId(), "${nextPayloadId()}", eventsBatch3)

            lastSentWidth = screenshot.bitmap.width
            lastSentHeight = screenshot.bitmap.height

            true
        } catch (e: Exception) {
            Log.e("ScreenshotInstrumentation", "Error uploading initing session with screenshot: ${e.message}", e)
            false
        }
    }

    /**
     * Encodes a bitmap to base64 string.
     */
    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(CompressFormat.JPEG, 80, outputStream) // 80% quality to reduce size
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}