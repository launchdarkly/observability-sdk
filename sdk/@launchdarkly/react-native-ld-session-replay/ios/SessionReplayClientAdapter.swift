import Foundation
import LaunchDarklyObservability // From Dependency A
import LaunchDarklySessionReplay

@objc(SessionReplayClientAdapter)
//@objcMembers
public class SessionReplayClientAdapter: NSObject {
  @objc public static let shared = SessionReplayClientAdapter()
  private let clientQueue = DispatchQueue(label: "com.launchdarkly.sessionreplay.client.queue")
  private var mobileKey: String?
  private var sessionReplayOptions: SessionReplayOptions?
  private var isLDClientState: LDClientState = .idle
  enum LDClientState {
    case idle, starting, started
  }
  
  private override init() {
    super.init()
  }
  
  @objc public func setMobileKey(_ mobileKey: String, options: NSDictionary?) {
    clientQueue.sync { [weak self] in
      guard let self else {
        return assertionFailure("[SessionReplayClientAdapter] setMobileKey called on deallocated object")
      }
      let key = mobileKey.trimmingCharacters(in: .whitespacesAndNewlines)
      guard !key.isEmpty else {
        return assertionFailure("[SessionReplayClientAdapter] setMobileKey called with empty key; session replay will not connect. Configure with a valid LaunchDarkly mobile key.")
      }
      
      let options = self.sessionReplayOptionsFrom(dictionary: options)
      
      self.mobileKey = key
      self.sessionReplayOptions = options
    }
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
          sessionBackgroundTimeout: 10
        )
      ),
      SessionReplay(options: options)
    ]
    /// we set the LDClient offline to stop communication with the LaunchDarkly servers.
    /// The React Native LDClient will be in charge of communicating with the LaunchDarkly servers.
    config.startOnline = false
    return config
  }
  
  private func makeContext() -> LDContext? {
    var contextBuilder = LDContextBuilder(
      key: "12345"
    )
    contextBuilder.kind("user")
    do {
      return try contextBuilder.build().get()
    } catch {
      NSLog("[SessionReplayAdapter] Failed to build LDContext: %@", error.localizedDescription)
      return nil
    }
  }
  
  private func setLDReplayEnabled(_ enabled: Bool, completion: @escaping () -> Void) {
    Task { @MainActor in
      /// If LDReplay state is different, toggle it
      guard LDReplay.shared.isEnabled != enabled else {
        return completion()
      }
      LDReplay.shared.isEnabled = enabled
      completion()
    }
  }
  
  private func start(mobileKey: String, options: SessionReplayOptions, completion: @escaping (Bool, String?) -> Void) {
    switch isLDClientState {
    case .idle:
      isLDClientState = .starting
      let config = self.makeConfig(mobileKey: mobileKey, options: options)
      let context = self.makeContext()
      LDClient
        .start(
          config: config,
          context: context,
          startWaitSeconds: 0) { timedOut in
            self.clientQueue.sync { [weak self] in
              self?.isLDClientState = .started
              self?.setLDReplayEnabled(true) {
                completion(true, nil)
              }
            }
          }
    case .starting:
      /// Client is starting, we must await until it finishes and state is started
      /// LDReplay will be started after LDClient finishes
      break
    case .started:
      /// Client is started, we can now focus on the session replay client
      /// Since this is the start method, we want to do so for LDReplay, set enabled to true
      setLDReplayEnabled(true) {
        completion(true, nil)
      }
      break
    }
  }
  
  @objc public func start(completion: @escaping (Bool, String?) -> Void) {
    clientQueue.sync { [weak self] in
      guard let self else {
        return assertionFailure("[SessionReplayClientAdapter] setMobileKey called on deallocated object")
      }
      guard let mobileKey = self.mobileKey, let options = self.sessionReplayOptions else {
        completion(false, "Client not initialized. Call SetMobileKey first.")
        return
      }
      self.start(mobileKey: mobileKey, options: options, completion: completion)
    }
  }
  
  /// LDClient should not be closed, will be offline all the times
  private func _stop(_ completion: @escaping () -> Void) {
    setLDReplayEnabled(false, completion: completion)
  }
  
  /// There is almost no reason to stop the LDClient. Normally, set the LDClient offline to stop communication with the LaunchDarkly servers. Stop the LDClient to stop recording events. There is no need to stop the LDClient prior to suspending, moving to the background, or terminating the app. The SDK will respond to these events as the system requires and as configured in LDConfig.
  ///
  /// So in order to not record anything from the Swift's LDClient, LDClient is configured to be offline in the start method
  /// LDClient is only needed as a holder of the SessionReplay plugin
  ///
  /// Stop is intended to provide a stop like API, internally is disabling session replay until app start it with start method
  @objc public func stop(completion: @escaping () -> Void) {
    _stop(completion)
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
