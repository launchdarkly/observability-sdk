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

    init {
        sdkKey = "640b692558eaef13a77c66b8" // TODO: at the moment this is hardcoded to the client side ID for the environment, need to figure this out
        graphqlClient = GraphQLClient("https://pub.observability.app.launchdarkly.com")

    }

    override val name: String = "screenshotter"

    override fun install(ctx: InstallationContext) {
        sessionManager = ctx.sessionManager
        _screenshotter = ComposeScreenshotter(sessionManager)
        graphqlClient = GraphQLClient("https://pub.observability.app.launchdarkly.com")
        replayApiService = SessionReplayApiService(graphqlClient)

        // Initialize replay session in background IO coroutine
        GlobalScope.launch(Dispatchers.IO) {
            val sessionId = sessionManager.getSessionId()
            replayApiService.initializeReplaySession(sdkKey, sessionId)
            replayApiService.identifyReplaySession(sessionId)

            // TODO: this is hacky for development
            _screenshotter.captureScreenshotNow()
        }

        // Hook up screenshot flow to upload routine
        GlobalScope.launch(Dispatchers.IO) {
            _screenshotter.screenshotFlow.collect { screenshot ->
                delay(10000)
                try {
                    val success = uploadScreenshot(screenshot)
                    if (success) {
                        Log.d("ScreenshotInstrumentation", "Successfully uploaded screenshot for session: ${screenshot.session}")
                    } else {
                        Log.w("ScreenshotInstrumentation", "Failed to upload screenshot for session: ${screenshot.session}")
                    }
                } catch (e: Exception) {
                    Log.e("ScreenshotInstrumentation", "Error processing screenshot upload: ${e.message}", e)
                }
            }
        }

        ctx.application.registerActivityLifecycleCallbacks(_screenshotter)
        
    }

    suspend fun uploadScreenshot(screenshot: Screenshot): Boolean = withContext(Dispatchers.IO) {
        try {
            val base64Image = encodeBitmapToBase64(screenshot.bitmap)
            val events = mutableListOf<Event>()

            val timestamp = System.currentTimeMillis()
            val metaEvent = Event(
                type = EventType.META,
                timestamp = timestamp,
                _sid = 1000,
                data = EventDataUnion.StandardEventData(
                    EventData(
                        href = "www.bogus.com",
                        width = 1080,
                        height = 2274
                    )
                ),
            )
            events.add(metaEvent)

            val snapShotEvent = Event(
                type = EventType.FULL_SNAPSHOT,
                timestamp = timestamp,
                _sid = 1001,
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
                                                        "width" to "1080",
                                                        "height" to "2274" // TODO: one day make this dynamic
                                                    )
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
            events.add(snapShotEvent)

            val reloadEvent = Event(
                type = EventType.CUSTOM,
                timestamp = timestamp,
                _sid = 1002,
                data = EventDataUnion.CustomEventDataWrapper(
                    Json.parseToJsonElement("""{"tag":"Reload","payload":"Android Demo"}""")
                )
            )
            events.add(reloadEvent)

            val viewportEvent = Event(
                type = EventType.CUSTOM,
                timestamp = timestamp,
                _sid = 1003,
                data = EventDataUnion.CustomEventDataWrapper(
                    Json.parseToJsonElement("""{"tag":"Viewport","payload":{"width":1080,"height":2274,"availWidth":1080,"availHeight":2274,"colorDepth":30,"pixelDepth":30,"orientation":0}}""")
                )
            )
            events.add(viewportEvent)

            // mouse interaction 1
            events.add(
                Event(
                    type = EventType.INCREMENTAL_SNAPSHOT,
                    timestamp = timestamp + 100,
                    _sid = 1004,
                    data = EventDataUnion.CustomEventDataWrapper(
                        Json.parseToJsonElement("""{"source":2,"type":6,"id":6}""")
                    )
                )
            )

            // mouse interaction 2
            events.add(
                Event(
                    type = EventType.INCREMENTAL_SNAPSHOT,
                    timestamp = timestamp + 500,
                    _sid = 1005,
                    data = EventDataUnion.CustomEventDataWrapper(
                        Json.parseToJsonElement("""{"source":1,"positions":[{"x":343,"y":483,"id":6,"timeOffset":0}]}""")
                    )
                )
            )

            // mouse interaction 3
            events.add(
                Event(
                    type = EventType.INCREMENTAL_SNAPSHOT,
                    timestamp = timestamp + 1500,
                    _sid = 1006,
                    data = EventDataUnion.CustomEventDataWrapper(
                        Json.parseToJsonElement("""{"source":1,"positions":[{"x":350,"y":482,"id":6,"timeOffset":-451},{"x":397,"y":454,"id":6,"timeOffset":-395},{"x":549,"y":325,"id":6,"timeOffset":-335}]}""")
                    )
                )
            )

            replayApiService.pushPayload(sdkKey, sessionManager.getSessionId(), "0", events)
            true
        } catch (e: Exception) {
            Log.e("ScreenshotInstrumentation", "Error uploading screenshot: ${e.message}", e)
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