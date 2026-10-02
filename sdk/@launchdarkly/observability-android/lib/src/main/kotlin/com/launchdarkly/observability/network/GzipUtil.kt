package com.launchdarkly.observability.network

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

internal object GzipUtil {
    fun gzip(data: ByteArray): ByteArray {
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { gzipStream ->
            gzipStream.write(data)
        }
        return byteStream.toByteArray()
    }
}
