import Foundation
import LaunchDarklyObservability // From Dependency A
import LaunchDarklySessionReplay

@objc(SessionReplayClientAdapter)
//@objcMembers
public class SessionReplayClientAdapter: NSObject {
  @objc public static let shared = SessionReplayClientAdapter()

  // Guarded by lock.
  private let lock = NSLock()
  private var mobileKey: String?
  private var sessionReplayOptions: SessionReplayOptions?
  // Each start()/stop() appends a new Task that awaits the previous one, serializing all work.
  private var lastTask: Task<Void, Never> = Task {}

  @MainActor private var initialized = false
  // The most recently identified LDContext. Defaults to nil (LDClient will use its own anonymous
  // context). Updated on each successful identify via afterIdentify.
  @MainActor private var cachedContext: LDContext? = nil

  private override init() {
    super.init()
  }

  @objc public func setMobileKey(_ mobileKey: String, options: NSDictionary?) {
    lock.lock()
    defer { lock.unlock() }
    let key = mobileKey.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !key.isEmpty else {
      return assertionFailure("[SessionReplayClientAdapter] setMobileKey called with empty key; session replay will not connect. Configure with a valid LaunchDarkly mobile key.")
    }
    self.mobileKey = key
    self.sessionReplayOptions = sessionReplayOptionsFrom(dictionary: options)
  }

  private func makeConfig(mobileKey: String, options: SessionReplayOptions) -> LDConfig {
    var config = LDConfig(
      mobileKey: mobileKey,
      autoEnvAttributes: .enabled
    )
    config.plugins = [
      Observability(
        options: .init(
          serviceName: options.serviceName,
          sessionBackgroundTimeout: 10,
          /// Disable the underlying KSCrash-based crash reporter that
          crashReporting: .init(source: .none)
        )
      ),
      SessionReplay(options: options)
    ]
    /// we set the LDClient offline to stop communication with the LaunchDarkly servers.
    /// The React Native LDClient will be in charge of communicating with the LaunchDarkly servers.
    /// offline is considered a short circuited timed out case
    config.startOnline = false
    return config
  }

  // Builds an LDContext from a [kind: key] map. Returns nil if the map is empty or a context
  // cannot be built. Mirrors buildContextFromKeys() in SessionReplayClientAdapter.kt.
  private func buildContextFromKeys(_ keys: [String: String]) -> LDContext? {
    guard let first = keys.first else { return nil }
    if keys.count == 1 {
      let (kind, key) = first
      var builder = LDContextBuilder(key: key)
      builder.kind(kind)
      guard case .success(let context) = builder.build() else {
        NSLog("[SessionReplayAdapter] Failed to build LDContext for kind=%@", kind)
        return nil
      }
      return context
    }
    var multiBuilder = LDMultiContextBuilder()
    for (kind, key) in keys {
      var builder = LDContextBuilder(key: key)
      builder.kind(kind)
      if case .success(let context) = builder.build() {
        multiBuilder.addContext(context)
      }
    }
    guard case .success(let context) = multiBuilder.build() else {
      NSLog("[SessionReplayAdapter] Failed to build multi-context")
      return nil
    }
    return context
  }

  @objc public func start(completion: @escaping (Bool, String?) -> Void) {
    lock.lock()
    defer { lock.unlock() }
    guard let mobileKey = mobileKey, let sessionReplayOptions = sessionReplayOptions else {
      completion(false, "Client not initialized. Call SetMobileKey first.")
      return
    }
    let prev = lastTask
    lastTask = Task { @MainActor [weak self] in
      await prev.value
      guard let self else { return }
      if !self.initialized {
        let config = self.makeConfig(mobileKey: mobileKey, options: sessionReplayOptions)
        let context = self.cachedContext
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
          LDClient.start(config: config, context: context, startWaitSeconds: 0) { _ in
            cont.resume()
          }
        }
        self.initialized = true
      } else {
        NSLog(
          "[SessionReplayClientAdapter] start: already initialized, re-applying isEnabled=%@",
          sessionReplayOptions.isEnabled ? "true" : "false"
        )
      }
      LDReplay.shared.isEnabled = sessionReplayOptions.isEnabled
      completion(true, nil)
    }
  }

  @objc public func afterIdentify(contextKeys: NSDictionary, canonicalKey: String, completed: Bool) {
    var keys = [String: String]()
    for (k, v) in contextKeys {
      if let kind = k as? String, let key = v as? String {
        keys[kind] = key
      }
    }
    lock.lock()
    defer { lock.unlock() }
    let prev = lastTask
    lastTask = Task { @MainActor [weak self] in
      await prev.value
      guard let self else { return }
      if completed {
        // If buildContextFromKeys returns nil, that's fine — LaunchDarkly will
        // use a default anonymous context.
        self.cachedContext = self.buildContextFromKeys(keys)
      }
      if self.initialized {
        LDReplay.shared.hookProxy?.afterIdentify(
          contextKeys: contextKeys,
          canonicalKey: canonicalKey,
          completed: completed
        )
      }
    }
  }

  /// There is almost no reason to stop the LDClient. Normally, set the LDClient offline to stop communication with the LaunchDarkly servers. Stop the LDClient to stop recording events. There is no need to stop the LDClient prior to suspending, moving to the background, or terminating the app. The SDK will respond to these events as the system requires and as configured in LDConfig.
  ///
  /// So in order to not record anything from the Swift's LDClient, LDClient is configured to be offline in the start method
  /// LDClient is only needed as a holder of the SessionReplay plugin
  ///
  /// Stop is intended to provide a stop like API, internally is disabling session replay until app start it with start method
  @objc public func stop(completion: @escaping () -> Void) {
    lock.lock()
    defer { lock.unlock() }
    let prev = lastTask
    lastTask = Task { @MainActor in
      await prev.value
      LDReplay.shared.isEnabled = false
      completion()
    }
  }
}

extension SessionReplayClientAdapter {
  private func sessionReplayOptionsFrom(dictionary: NSDictionary?) -> SessionReplayOptions {
    // Handle nil dictionary by using all default values
    guard let dictionary = dictionary else {
      let privacy = SessionReplayOptions.PrivacyOptions(
        maskTextInputs: true,
        maskWebViews: false,
        maskLabels: false,
        maskImages: false,
        maskUIViews: [],
        unmaskUIViews: [],
        ignoreUIViews: [],
        maskAccessibilityIdentifiers: [],
        unmaskAccessibilityIdentifiers: [],
        ignoreAccessibilityIdentifiers: [],
        minimumAlpha: 0.02
      )
      return .init(
        isEnabled: true,
        serviceName: "sessionreplay-react-native",
        privacy: privacy
      )
    }

    let privacy = SessionReplayOptions.PrivacyOptions(
      maskTextInputs: dictionary["maskTextInputs"] as? Bool ?? true,
      maskWebViews: dictionary["maskWebViews"] as? Bool ?? false,
      maskLabels: dictionary["maskLabels"] as? Bool ?? false,
      maskImages: dictionary["maskImages"] as? Bool ?? false,
      maskUIViews: [], /// Not supported, since AnyClass has type erased and it is very likely is not serializable
      unmaskUIViews: [], /// Not supported, since AnyClass has type erased and it is very likely is not serializable
      ignoreUIViews: [], /// Not supported, since AnyClass has type erased and it is very likely is not serializable
      maskAccessibilityIdentifiers:
        dictionary["maskAccessibilityIdentifiers"] as? [String] ?? [],
      unmaskAccessibilityIdentifiers:
        dictionary["unmaskAccessibilityIdentifiers"] as? [String] ?? [],
      ignoreAccessibilityIdentifiers:
        dictionary["ignoreAccessibilityIdentifiers"] as? [String] ?? [],
      minimumAlpha:
        CGFloat((dictionary["minimumAlpha"] as? NSNumber)?.doubleValue ?? 0.02)
    )

    return .init(
      isEnabled: dictionary["isEnabled"] as? Bool ?? true,
      serviceName: dictionary["serviceName"] as? String ?? "sessionreplay-react-native",
      privacy: privacy
    )
  }
}
