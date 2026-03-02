package com.example.androidobservability.benchmark

import android.graphics.Bitmap
import com.launchdarkly.observability.replay.Event
import com.launchdarkly.observability.replay.ReplayOptions
import com.launchdarkly.observability.replay.capture.ExportDiffManager
import com.launchdarkly.observability.replay.capture.ImageCaptureService
import com.launchdarkly.observability.replay.exporter.RRWebEventGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class BenchmarkExecutor {
    data class CompressionResult(
        val compression: ReplayOptions.CompressionMethod,
        val bytes: Int,
        val executionTimeNanos: Long,
    )

    private val compressionMethods = listOf(
        ReplayOptions.CompressionMethod.ScreenImage,
        ReplayOptions.CompressionMethod.OverlayTiles(layers = 15, backtracking = false),
        ReplayOptions.CompressionMethod.OverlayTiles(layers = 15, backtracking = true),
    )

    suspend fun compression(framesDirectory: File, runs: Int = 1): List<CompressionResult> =
        withContext(Dispatchers.Default) {
            val frames = RawFrameReader(framesDirectory).toList()

            val results = mutableListOf<CompressionResult>()
            val runCount = maxOf(1, runs)

            for (method in compressionMethods) {
                var bytes = 0
                var executionTimeNanos = 0L

                for (i in 0 until runCount) {
                    val runResult = runCompression(method, frames)
                    bytes = runResult.bytes
                    executionTimeNanos += runResult.executionTimeNanos
                }

                results.add(CompressionResult(method, bytes, executionTimeNanos))
            }

            frames.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
            results
        }

    private fun runCompression(
        method: ReplayOptions.CompressionMethod,
        sourceFrames: List<ImageCaptureService.RawFrame>,
    ): CompressionResult {
        val copies = sourceFrames.map { frame ->
            ImageCaptureService.RawFrame(
                bitmap = frame.bitmap.copy(frame.bitmap.config ?: Bitmap.Config.ARGB_8888, true),
                timestamp = frame.timestamp,
                orientation = frame.orientation,
            )
        }

        val exportDiffManager = ExportDiffManager(compression = method, scale = 1f)
        val eventGenerator = RRWebEventGenerator(canvasDrawEntourage = 300)
        val json = Json
        var bytes = 0
        var isFirst = true

        val start = System.nanoTime()

        for (frame in copies) {
            val exportFrame = exportDiffManager.createCaptureEvent(frame, "benchmark") ?: continue

            val events = if (isFirst) {
                isFirst = false
                eventGenerator.generateCaptureFullEvents(exportFrame)
            } else {
                eventGenerator.generateCaptureIncrementalEvents(exportFrame)
            }

            try {
                val data = json.encodeToString(ListSerializer(Event.serializer()), events)
                bytes += data.toByteArray(Charsets.UTF_8).size
            } catch (_: Exception) {
            }
        }

        val elapsed = System.nanoTime() - start
        return CompressionResult(
            compression = method,
            bytes = bytes,
            executionTimeNanos = elapsed,
        )
    }
}
