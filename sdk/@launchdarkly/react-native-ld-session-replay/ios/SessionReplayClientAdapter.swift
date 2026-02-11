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
  private var isLDClientStarted: Bool = false
  
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
  
  @objc public func start(completion: @escaping (Bool, String?) -> Void) {
    clientQueue.sync { [weak self] in
      guard let self else {
        return assertionFailure("[SessionReplayClientAdapter] setMobileKey called on deallocated object")
      }
      guard !self.isLDClientStarted else {
        return completion(true, nil)
      }
      guard let mobileKey = self.mobileKey, let sessionReplayOptions = self.sessionReplayOptions else {
        completion(false, "Client not initialized. Call SetMobileKey first.")
        return
      }
      let config = { () -> LDConfig in
        var config = LDConfig(
          mobileKey: mobileKey,
          autoEnvAttributes: .enabled
        )
        config.plugins = [
          Observability(
            options: .init(
              serviceName: sessionReplayOptions.serviceName,
              sessionBackgroundTimeout: 10
            )
          ),
          SessionReplay(options: sessionReplayOptions)
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
      
      LDClient.start(
        config: config,
        context: context,
        startWaitSeconds: 5.0,
        completion: { [weak self] (timedOut: Bool) -> Void in
          self?.isLDClientStarted = true
          Task { @MainActor in
            LDReplay.shared.isEnabled = true
          }
          completion(true, nil)
        }
      )
    }    
  }
  
  @objc public func stop() {
    clientQueue.sync { [weak self] in
      guard let self, self.isLDClientStarted else {
        return
      }
      Task { @MainActor in
        LDReplay.shared.isEnabled = false
      }
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
