package com.launchdarkly.observability.client

/**
 * An app-launch analytics event broadcast to in-process consumers such as Session Replay.
 *
 * Drives both the taxonomy span (`app_launch`) and the Session Replay timeline breadcrumb
 * (`Launch`). Emitted once per process launch. Mirrors [AppLifecycleSignal].
 *
 * @property launchType The product-milestone of the launch (`install` / `update` / `relaunch`),
 *   orthogonal to the cold/warm startup-performance dimension carried by [startType].
 * @property version Current app version (`PackageInfo.versionName`).
 * @property build Current build number (`PackageInfo.longVersionCode`).
 * @property previousVersion Version before an `update` launch; `null` otherwise.
 * @property startType Cold vs warm process start, when known.
 * @property startDurationMs Time from process start to launch detection, in milliseconds, when known.
 * @property timestamp Event time, in milliseconds since epoch.
 */
data class AppLaunchSignal(
    val launchType: LaunchType,
    val version: String?,
    val build: String?,
    val previousVersion: String?,
    val startType: StartType?,
    val startDurationMs: Long?,
    val timestamp: Long = System.currentTimeMillis(),
) {
    enum class LaunchType(val wireValue: String) {
        RELAUNCH("relaunch"),
        INSTALL("install"),
        UPDATE("update"),
    }

    enum class StartType(val wireValue: String) {
        COLD("cold"),
        WARM("warm"),
    }
}
