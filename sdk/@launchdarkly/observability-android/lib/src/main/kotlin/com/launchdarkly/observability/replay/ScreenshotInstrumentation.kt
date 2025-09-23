package com.launchdarkly.observability.replay

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.util.Base64
import android.util.Log
import com.google.auto.service.AutoService
import io.opentelemetry.android.instrumentation.AndroidInstrumentation
import io.opentelemetry.android.instrumentation.InstallationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

@AutoService(AndroidInstrumentation::class)
class ScreenshotInstrumentation : AndroidInstrumentation {
    private lateinit var _screenshotter: ComposeScreenshotter
    private lateinit var httpClient: OkHttpClient
    private lateinit var backendUrl: String
    private lateinit var sdkKey: String

    override val name: String = "screenshotter"

    override fun install(ctx: InstallationContext) {
        _screenshotter = ComposeScreenshotter(ctx.sessionManager)
        
        // Initialize OkHttp client
        httpClient = OkHttpClient.Builder()
            .build()
        
        // TODO: These should be passed from configuration
        backendUrl = "https://api.launchdarkly.com"
        sdkKey = ""

        ctx.application.registerActivityLifecycleCallbacks(_screenshotter)

        Log.d("Todd was here", ctx.sessionManager.getSessionId())
    }

    suspend fun uploadScreenshot(screenshot: Screenshot): Boolean = withContext(Dispatchers.IO) {
        try {
            val base64Image = encodeBitmapToBase64(screenshot.bitmap)
            val requestBody = createRequestBody(base64Image, screenshot.timestamp, screenshot.session)

            val request = Request.Builder()
                .url("$backendUrl/v1/screenshots")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $sdkKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                Log.d("ScreenshotInstrumentation", "Screenshot uploaded successfully for session ${screenshot.session}")
                true
            } else {
                Log.w("ScreenshotInstrumentation", "Failed to upload screenshot: ${response.code} ${response.message}")
                false
            }
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

    /**
     * Creates the JSON request body for the screenshot upload.
     */
    private fun createRequestBody(base64Image: String, timestamp: Long, session: String): okhttp3.RequestBody {
        val json = """
        {
            "image": "$base64Image",
            "timestamp": $timestamp,
            "session": "$session",
            "format": "jpeg"
        }
        """.trimIndent()
        
        return json.toRequestBody("application/json".toMediaType())
    }

}