import UIKit.UIApplication
import Instrumentation
import Combine

final class Session {
    private var id: String
    private var startTime: Date
    private var backgroundTime: Date?
    private var options: SessionOptions
    private var cancellables = Set<AnyCancellable>()
    var sessionAttributes: [String: String] {
        [
            "session.id": id,
            "session.start_time": String(format: "%.0f", startTime.timeIntervalSince1970)
        ]
    }
    private var appState = AppLifecycleState.unattached
    
    var sessionInfo: SessionInfo {
        .init(
            id: id,
            startTime: startTime
        )
    }
    
    init(
        id: String = UUID().uuidString,
        startTime: Date = Date.now,
        options: SessionOptions
    ) {
        self.id = id
        self.startTime = startTime
        self.options = options
    }
    
    func onWillTerminate(
        _ handler: (@Sendable () -> Void)?,
    ) {
        NotificationCenter.default.publisher(for: UIApplication.willTerminateNotification)
        .subscribe(on: RunLoop.main)
        .receive(on: RunLoop.main)
        .sink { _ in
            handler?()
        }
        .store(in: &cancellables)
    }
    
    func start(
        onWillEndSession: (@Sendable (_ sessionId: String) -> Void)?,
        onDidStartSession: (@Sendable (_ sessionId: String) -> Void)?
    ) {
        guard let onWillEndSession, let onDidStartSession else { return }
        
        Publishers.MergeMany(
            NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification),
            NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)
        )
        .subscribe(on: RunLoop.main)
        .receive(on: RunLoop.main)
        .sink { [weak self] notification in
            guard let self else { return }
            let oldAppState = self.appState
            let newState = update(state: oldAppState, message: notification.name)
            self.appState = newState
            switch newState {
            case .foregroundActive:
                guard AppLifecycleState.notInForeground.contains(oldAppState), let backgroundTime = self.backgroundTime else { return }
                let timeInBackground = Date.now.timeIntervalSince1970 - backgroundTime.timeIntervalSince1970
                if timeInBackground >= self.options.timeout {
                    onWillEndSession(self.sessionInfo.id)
                    self.resetSession()
                    onDidStartSession(self.sessionInfo.id)
                }
                self.backgroundTime = nil
            case .foregroundInactive:
                break
            case .background, .unattached, .suspended:
                self.backgroundTime = Date.now
            }
        }
        .store(in: &cancellables)
    }
    
    private func resetSession() {
        let oldSessionId = sessionInfo.id
        let newSessionId = UUID().uuidString
        let oldStartTime = startTime
        
        id = newSessionId
        let newStartTime = Date.now
        startTime = newStartTime
        
        if options.isDebug {
            print("🔄 Session reset: \(oldSessionId) -> \(sessionInfo.id)")
            let dateInterval = DateInterval(start: oldStartTime, end: newStartTime)
            print("Session duration: \(dateInterval.duration) seconds")
        }
    }
}

enum AppLifecycleState {
    case unattached
    case foregroundInactive
    case foregroundActive
    case suspended
    case background
    
    static let notInForeground: [Self] = [
        .unattached, .suspended, .background
    ]
}
private func update(
    state: AppLifecycleState,
    message: Notification.Name
) -> AppLifecycleState {
    switch message {
    case UIApplication.didFinishLaunchingNotification:
        return .foregroundInactive
        
    case UIApplication.willResignActiveNotification:
        return .background
    case UIApplication.didEnterBackgroundNotification:
        return .background
        
    case UIApplication.willEnterForegroundNotification:
        return .foregroundInactive
    case UIApplication.didBecomeActiveNotification:
        return .foregroundActive
    
    case UIApplication.willTerminateNotification:
        return .suspended
    default:
        return state
    }
}
