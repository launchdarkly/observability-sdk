import Foundation
import LaunchDarklyObservability // From Dependency A
import LaunchDarklySessionReplay

@objc(SessionReplayAdapter)
//@objcMembers
public class SessionReplayAdapter: NSObject {
  @objc public static let shared = SessionReplayAdapter()
  private var client: Client?
  
  private override init() {
    super.init()
  }
  
  @objc public func setMobileKey(_ mobileKey: String, options: NSDictionary?) {
    // Close the previous client's LDClient before replacing it to prevent resource leaks
    if let previousClient = self.client {
      previousClient.close()
    }
    let options = sessionReplayOptionsFrom(dictionary: options)
    self.client = Client(mobileKey: mobileKey, options: options)
  }
  
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
  
  @objc public func start(completion: @escaping (Bool, String?) -> Void) {
    guard let client else {
      completion(false, "Client not initialized. Call configure first.")
      return
    }
    client.start(completion: completion)
  }
  
  @objc public func stop() {
    guard let client else { return }
    client.stop()
  }
}

fileprivate class Client {
  private let mobileKey: String
  private let options: SessionReplayOptions
  private var isStarted: Bool = false
  private var isStarting: Bool = false
  private var closePending: Bool = false
  private let startLock = NSLock()

  init(mobileKey: String, options: SessionReplayOptions) {
    self.mobileKey = mobileKey
    self.options = options
  }
  
  lazy var config = { [weak self] () -> LDConfig in
    var config = LDConfig(
      mobileKey: self?.mobileKey ?? "",
      autoEnvAttributes: .enabled
    )
    config.plugins = [
      Observability(
        options: .init(
          serviceName: "ios-app",
          sessionBackgroundTimeout: 3
        )
      ),
      SessionReplay(options: self?.options ?? .init())
    ]
    /// Controls LDClient start behavior. When true, calling start causes LDClient to go online.
    /// When false, calling start causes LDClient to remain offline. 
    /// If offline at start, set the client online to receive flag updates. (Default: true)
    config.startOnline = false
    return config
  }()
  
  let context: LDContext? = {
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
  }()
  
  func start(completion: @escaping (Bool, String?) -> Void) {
    guard let context = context else {
      let error = "Cannot start session replay: context creation failed"
      NSLog("[SessionReplayAdapter] %@", error)
      completion(false, error)
      return
    }

    startLock.lock()
    if isStarting {
      startLock.unlock()
      let error = "Start already in progress"
      NSLog("[SessionReplayAdapter] %@", error)
      completion(false, error)
      return
    }
    isStarting = true
    // Close any existing LDClient before starting a new one to prevent resource leaks
    if isStarted, let existingClient = LDClient.get() {
      existingClient.close()
      isStarted = false
      NSLog("[SessionReplayAdapter] Closed existing LDClient before starting new one")
    }
    startLock.unlock()

    LDClient.start(
      config: config,
      context: context,
      startWaitSeconds: 5.0,
      completion: { [weak self] (timedOut: Bool) -> Void in
        guard let self else { return }
        self.startLock.lock()
        self.isStarting = false
        let shouldClosePending = self.closePending
        if shouldClosePending { self.closePending = false }
        if timedOut, self.config.startOnline == true {
          let error = "Session replay initialization timed out after 5 seconds"
          NSLog("[SessionReplayAdapter] ⚠️ %@", error)
          self.startLock.unlock()
          if shouldClosePending, let ldClient = LDClient.get() {
            self.closePendingClient(ldClient: ldClient)
          }
          completion(false, error)
        } else {
          self.isStarted = true
          NSLog("[SessionReplayAdapter] ✅ Session replay started successfully")
          self.startLock.unlock()
          if shouldClosePending, let ldClient = LDClient.get() {
            self.closePendingClient(ldClient: ldClient)
            completion(false, "Session replay was stopped during initialization")
          } else {
            completion(true, nil)
          }
        }
      }
    )
  }
  
  func stop() {
    // Stop session replay by disabling it via LDReplay.shared
    // LDReplay.shared.isEnabled is @MainActor isolated, so we need to mutate it on the main actor
    Task { @MainActor in
      LDReplay.shared.isEnabled = false
      NSLog("[SessionReplayAdapter] Session replay stopped")
    }
    // Close the LDClient to release network connections and background tasks
    close()
  }
  
  func close() {
    startLock.lock()
    // If start() is in progress, record that close was requested. The completion handler
    // will close the client when LDClient.start() finishes, so we don't leave replay running.
    if isStarting {
      closePending = true
      startLock.unlock()
      NSLog("[SessionReplayAdapter] Close requested while start in progress; will close when init completes")
      return
    }
    guard isStarted else {
      startLock.unlock()
      return
    }
    isStarted = false
    startLock.unlock()

    // LDClient is a singleton, so we access it via LDClient.get()
    // and call close() to properly shut down network connections and background tasks
    if let ldClient = LDClient.get() {
      ldClient.close()
      NSLog("[SessionReplayAdapter] LDClient closed and resources released")
    }
  }

  /// Called from the start() completion when closePending was set. Ensures replay is disabled
  /// and the client is closed, so a stop() during init is fully honored.
  private func closePendingClient(ldClient: LDClient) {
    Task { @MainActor in
      LDReplay.shared.isEnabled = false
    }
    ldClient.close()
    startLock.lock()
    isStarted = false
    startLock.unlock()
    NSLog("[SessionReplayAdapter] LDClient closed (was pending close during init)")
  }
}
