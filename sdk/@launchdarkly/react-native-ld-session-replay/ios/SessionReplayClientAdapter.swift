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
  private var ldReplayState: LDReplayState = .idle
  enum LDReplayState {
    case idle, starting, started, stopping, stopped
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
  
  @objc public func start(completion: @escaping (Bool, String?) -> Void) {
    clientQueue.sync { [weak self] in
      guard let self else {
        return assertionFailure("[SessionReplayClientAdapter] setMobileKey called on deallocated object")
      }
      /// Make sure LDClient is started a single time, no need to close the LDClient, since it was configured as offline mode, we will be no recording  events
      /// This means, we set the LDClient offline to stop communication with the LaunchDarkly servers.
      guard !self.isLDClientStarted else {
        /// since LDClient is already started, we just need to handle LDReplay to be enabled when start method is called
        switch self.ldReplayState {
        case .idle, .stopped:
          self.ldReplayState = .starting
          Task { @MainActor in
            if !LDReplay.shared.isEnabled {
              LDReplay.shared.isEnabled = true
              self.ldReplayState = .started
            } else {
              self.ldReplayState = .started
            }
            completion(true, nil)
          }
          return
        case .starting, .started, .stopping:
          return completion(true, nil)
        }
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
        /// we set the LDClient offline to stop communication with the LaunchDarkly servers.
        /// The React Native LDClient will be in charge of communicating with the LaunchDarkly servers.
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
          self?.clientQueue.sync {
            self?.isLDClientStarted = true
            self?.ldReplayState = .starting
            Task { @MainActor in
              LDReplay.shared.isEnabled = true
              self?.ldReplayState = .started
            }
            completion(true, nil)
          }
        }
      )
    }    
  }
  
  /// There is almost no reason to stop the LDClient. Normally, set the LDClient offline to stop communication with the LaunchDarkly servers. Stop the LDClient to stop recording events. There is no need to stop the LDClient prior to suspending, moving to the background, or terminating the app. The SDK will respond to these events as the system requires and as configured in LDConfig.
  ///
  /// So in order to not record anything from the Swift's LDClient, LDClient is configured to be offline in the start method
  /// LDClient is only needed as a holder of the SessionReplay plugin
  ///
  /// Stop is intended to provide a stop like API, internally is disabling session replay until app start it with start method
  @objc public func stop() {
    clientQueue.sync { [weak self] in
      guard self?.ldReplayState == .started else { return }
      
      /// Set the isLDReplayStarted to false to prevent the code to be executed again if stop is called several times
      self?.ldReplayState = .stopping
      Task { @MainActor in
        LDReplay.shared.isEnabled = false
        self?.ldReplayState = .stopped
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
