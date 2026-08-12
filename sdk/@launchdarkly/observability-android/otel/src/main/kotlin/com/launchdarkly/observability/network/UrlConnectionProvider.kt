package com.launchdarkly.observability.network

import java.net.HttpURLConnection

/**
 * Opens the HTTP connections used by the SDK's HTTP clients, so tests can substitute a fake
 * connection without reaching the network.
 */
interface UrlConnectionProvider {
    fun openConnection(url: String): HttpURLConnection
}
