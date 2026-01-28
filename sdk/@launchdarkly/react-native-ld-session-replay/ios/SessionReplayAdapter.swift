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
    
    // Close any existing LDClient before starting a new one to prevent resource leaks
    // This handles the case where start() is called multiple times or after reconfiguration
    if isStarted, let existingClient = LDClient.get() {
      existingClient.close()
      isStarted = false
      NSLog("[SessionReplayAdapter] Closed existing LDClient before starting new one")
    }
    
    LDClient.start(
      config: config,
      context: context,
      startWaitSeconds: 5.0,
      completion: { [weak self] (timedOut: Bool) -> Void in
        if timedOut {
          let error = "Session replay initialization timed out after 5 seconds"
          NSLog("[SessionReplayAdapter] ⚠️ %@", error)
          completion(false, error)
        } else {
          self?.isStarted = true
          NSLog("[SessionReplayAdapter] ✅ Session replay started successfully")
          completion(true, nil)
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
    // Only close if we actually started the client
    guard isStarted else { return }
    
    // LDClient is a singleton, so we access it via LDClient.get()
    // and call close() to properly shut down network connections and background tasks
    if let ldClient = LDClient.get() {
      ldClient.close()
      isStarted = false
      NSLog("[SessionReplayAdapter] LDClient closed and resources released")
    }
  }
}
