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
  
  @objc public func setMobileKey(_ mobileKey: String, options: NSDictionary) {
    let options = sessionReplayOptionsFrom(dictionary: options)
    self.client = Client(mobileKey: mobileKey, options: options)
//    self.client = Client(mobileKey: mobileKey, options: .init())
  }
  
  private func sessionReplayOptionsFrom(dictionary: NSDictionary) -> SessionReplayOptions {    
    
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
//      SessionReplay(
//        options: .init(
//          isEnabled: true,
//          privacy: .init(
//            maskTextInputs: true,
//            maskWebViews: false,
//            maskImages: false,
//            maskAccessibilityIdentifiers: ["email-field", "password-field"]
//          )
//        )
//      )
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
    LDClient.start(
      config: config,
      context: context,
      startWaitSeconds: 5.0,
      completion: { (timedOut: Bool) -> Void in
        if timedOut {
          let error = "Session replay initialization timed out after 5 seconds"
          NSLog("[SessionReplayAdapter] ⚠️ %@", error)
          completion(false, error)
        } else {
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
  }
}
