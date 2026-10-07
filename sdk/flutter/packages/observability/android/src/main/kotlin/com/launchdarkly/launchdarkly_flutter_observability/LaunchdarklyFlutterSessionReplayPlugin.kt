package com.launchdarkly.launchdarkly_flutter_observability

import android.app.Application
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodChannel

/**
 * Flutter plugin entry point for `launchdarkly_flutter_observability`. Wires
 * the pigeon-generated [LDNativeApi] host API to [LDNativeApiImpl], which
 * boots the LaunchDarkly observability + session replay native stack — a port
 * of `LDNative.cs` / `ObservabilityBridge.kt` from
 * `sdk/@launchdarkly/mobile-dotnet`.
 *
 * Implements [ActivityAware] so we can hand the host [android.app.Activity]
 * to the SDK after init. Without it the LD session replay SDK misses its
 * `onActivityCreated` event for the current activity (Flutter attaches the
 * plugin _after_ the activity is created) and never starts capturing the
 * view tree, which manifests as a black/blank session replay.
 */
class LaunchdarklyFlutterSessionReplayPlugin : FlutterPlugin, ActivityAware {
    private var impl: LDNativeApiImpl? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val application = binding.applicationContext.applicationContext as Application
        val channel = MethodChannel(binding.binaryMessenger, CAPTURE_CHANNEL_NAME)
        val newImpl = LDNativeApiImpl(application, channel)
        LDNativeApi.setUp(binding.binaryMessenger, newImpl)
        impl = newImpl
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        LDNativeApi.setUp(binding.binaryMessenger, null)
        impl = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        impl?.activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        impl?.activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        impl?.activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        impl?.activity = null
    }

    private companion object {
        const val CAPTURE_CHANNEL_NAME = "launchdarkly_flutter_session_replay/capture"
    }
}
