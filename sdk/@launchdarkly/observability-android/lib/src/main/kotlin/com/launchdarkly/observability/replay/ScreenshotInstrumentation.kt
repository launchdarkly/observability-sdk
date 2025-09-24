package com.launchdarkly.observability.replay

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.util.Base64
import android.util.Log
import com.google.auto.service.AutoService
import com.launchdarkly.observability.network.CustomEventData
import com.launchdarkly.observability.network.Event
import com.launchdarkly.observability.network.EventData
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@AutoService(AndroidInstrumentation::class)
class ScreenshotInstrumentation : AndroidInstrumentation {
    private lateinit var _screenshotter: ComposeScreenshotter
    private var graphqlClient: GraphQLClient
    private lateinit var samplingApiService: SessionReplayApiService
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
        samplingApiService = SessionReplayApiService(graphqlClient)

        // Initialize replay session in background IO coroutine
        GlobalScope.launch(Dispatchers.IO) {
            samplingApiService.initializeReplaySession(sdkKey, sessionManager.getSessionId())
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
                data = EventData(
                    href = "www.bogus.com",
                    width = 1080,
                    height = 2400
                ),
            )
            events.add(metaEvent)

            val snapShotEvent = Event(
                type = EventType.FULL_SNAPSHOT,
                timestamp = timestamp,
                _sid = 1001,
                data = EventData(
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
                                                    "height" to "2400" // TODO: one day make this dynamic
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                    ),
                ),
            )
            events.add(snapShotEvent)

            val reloadEvent = Event(
                type = EventType.CUSTOM,
                timestamp = timestamp,
                _sid = 1002,
                data = EventData(
                    tag = "Reload",
                    payload = "Android Demo"
                )
            )
            events.add(reloadEvent)



            samplingApiService.pushPayload(sdkKey, sessionManager.getSessionId(), "0", events)
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

//    /**
//     * Creates the JSON request body for the screenshot upload.
//     */
//    private fun createRequestBody(base64Image: String, timestamp: Long, session: String): okhttp3.RequestBody {
//        val json = """
//        {
//            "image": "$base64Image",
//            "timestamp": $timestamp,
//            "session": "$session",
//            "format": "jpeg"
//        }
//        """.trimIndent()
//
//        return json.toRequestBody("application/json".toMediaType())
//    }

}