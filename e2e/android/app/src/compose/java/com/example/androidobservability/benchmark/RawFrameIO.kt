package com.example.androidobservability.benchmark

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.launchdarkly.observability.replay.capture.ImageCaptureService
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.util.UUID

class RawFrameWriter(baseDir: File) {
    val directory: File = File(baseDir, "RawFrames-${UUID.randomUUID()}")
    private var frameIndex = 0
    private var imageIndex = 0
    private var lastImageBytes: ByteArray? = null
    private val csvWriter: PrintWriter

    init {
        directory.mkdirs()
        val csvFile = File(directory, "frames.csv")
        csvWriter = PrintWriter(FileOutputStream(csvFile), true)
        csvWriter.println("frameIndex,imageIndex,timestamp,orientation")
    }

    fun write(rawFrame: ImageCaptureService.RawFrame) {
        val index = frameIndex++
        val pngBytes = rawFrame.bitmap.toPngBytes()
            ?: error("Failed to encode bitmap to PNG")

        val currentImageIndex: Int
        if (pngBytes.contentEquals(lastImageBytes)) {
            currentImageIndex = imageIndex - 1
        } else {
            currentImageIndex = imageIndex
            File(directory, "%06d.png".format(currentImageIndex)).writeBytes(pngBytes)
            lastImageBytes = pngBytes
            imageIndex++
        }

        csvWriter.println("$index,$currentImageIndex,${rawFrame.timestamp},${rawFrame.orientation}")
    }

    fun close() {
        csvWriter.close()
    }
}

private fun Bitmap.toPngBytes(): ByteArray? {
    val stream = ByteArrayOutputStream()
    return if (compress(Bitmap.CompressFormat.PNG, 100, stream)) {
        stream.toByteArray()
    } else {
        null
    }
}

// MARK: - RawFrameReader

class RawFrameReader(private val directory: File) : Sequence<ImageCaptureService.RawFrame> {
    private val rows: List<String>

    init {
        val csvFile = File(directory, "frames.csv")
        rows = csvFile.readText().lines().drop(1).filter { it.isNotBlank() }
    }

    override fun iterator(): Iterator<ImageCaptureService.RawFrame> = FrameIterator(directory, rows)

    private class FrameIterator(
        private val directory: File,
        private val rows: List<String>,
    ) : Iterator<ImageCaptureService.RawFrame> {
        private var index = 0
        private val imageCache = mutableMapOf<Int, Bitmap>()

        override fun hasNext(): Boolean = index < rows.size

        override fun next(): ImageCaptureService.RawFrame {
            val frame = parse(rows[index])
                ?: throw NoSuchElementException("Failed to parse frame at index $index")
            index++
            return frame
        }

        private fun parse(line: String): ImageCaptureService.RawFrame? {
            val columns = line.split(",")
            if (columns.size < 4) return null
            val imageIndex = columns[1].trim().toIntOrNull() ?: return null
            val timestamp = columns[2].trim().toDoubleOrNull()?.let { (it * 1000).toLong() } ?: return null
            val orientation = columns[3].trim().toIntOrNull() ?: return null

            val bitmap = imageCache.getOrPut(imageIndex) {
                val imageFile = File(directory, "%06d.png".format(imageIndex))
                BitmapFactory.decodeFile(imageFile.absolutePath) ?: return null
            }

            return ImageCaptureService.RawFrame(
                bitmap = bitmap,
                timestamp = timestamp,
                orientation = orientation,
            )
        }
    }
}
