package com.launchdarkly.observability.network

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@com.launchdarkly.observability.InternalObservabilityApi
object GzipUtil {
    fun gzip(data: ByteArray): ByteArray {
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { gzipStream ->
            gzipStream.write(data)
        }
        return byteStream.toByteArray()
    }
}
