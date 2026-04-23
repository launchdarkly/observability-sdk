package com.sessionreplayreactnative

import android.app.Application
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule

@ReactModule(name = SessionReplayReactNativeModule.NAME)
class SessionReplayReactNativeModule(reactContext: ReactApplicationContext) :
  NativeSessionReplayReactNativeSpec(reactContext) {

  override fun getName(): String = NAME

  override fun configure(mobileKey: String, options: ReadableMap?, promise: Promise) {
    val key = mobileKey.trim()
    if (key.isEmpty()) {
      promise.reject("invalid_mobile_key", "Session replay requires a non-empty mobile key.", null)
      return
    }
    try {
      SessionReplayClientAdapter.shared.setMobileKey(key, options)
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("configure_failed", e.message, e)
    }
  }

  override fun startSessionReplay(promise: Promise) {
    val application = reactApplicationContext.applicationContext as? Application
    if (application == null) {
      promise.reject("start_failed", "Could not obtain application context.", null)
      return
    }
    try {
      SessionReplayClientAdapter.shared.start(application, reactApplicationContext.getCurrentActivity()) { success, errorMessage ->
        if (success) {
          promise.resolve(null)
        } else {
          promise.reject("start_failed", errorMessage ?: "Session replay failed to start.", null)
        }
      }
    } catch (e: Exception) {
      promise.reject("start_failed", e.message, e)
    }
  }

  override fun stopSessionReplay(promise: Promise) {
    try {
      SessionReplayClientAdapter.shared.stop {
        promise.resolve(null)
      }
    } catch (e: Exception) {
      promise.reject("stop_failed", e.message, e)
    }
  }

  override fun afterIdentify(
    contextKeys: ReadableMap,
    canonicalKey: String,
    completed: Boolean,
    promise: Promise
  ) {
    try {
      SessionReplayClientAdapter.shared.afterIdentify(contextKeys, canonicalKey, completed)
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject("after_identify_failed", e.message, e)
    }
  }

  companion object {
    const val NAME = "SessionReplayReactNative"
  }
}
