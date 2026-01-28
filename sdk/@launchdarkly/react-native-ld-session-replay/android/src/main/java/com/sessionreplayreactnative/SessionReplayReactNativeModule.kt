package com.sessionreplayreactnative

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.bridge.Promise

@ReactModule(name = SessionReplayReactNativeModule.NAME)
class SessionReplayReactNativeModule(reactContext: ReactApplicationContext) :
  NativeSessionReplayReactNativeSpec(reactContext) {

  override fun getName(): String {
    return NAME
  }

  override fun configure(
    mobileKey: String,
    options: com.facebook.react.bridge.ReadableMap?,
    promise: Promise
  ) {
    promise.reject(
      "NOT_SUPPORTED",
      "Session replay is not yet supported on Android. iOS support is available.",
      null
    )
  }

  override fun startSessionReplay(promise: Promise) {
    promise.reject(
      "NOT_SUPPORTED",
      "Session replay is not yet supported on Android. iOS support is available.",
      null
    )
  }

  override fun stopSessionReplay(promise: Promise) {
    promise.reject(
      "NOT_SUPPORTED",
      "Session replay is not yet supported on Android. iOS support is available.",
      null
    )
  }

  companion object {
    const val NAME = "SessionReplayReactNative"
  }
}
