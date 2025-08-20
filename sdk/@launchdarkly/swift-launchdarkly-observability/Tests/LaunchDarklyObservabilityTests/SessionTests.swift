import Testing
@testable import Client
import UIKit.UIApplication

struct SessionTests {
    @Test func appLifeCycleBasedSession() async throws {
        let sessionOptions = SessionOptions(timeout: 1)
        let session = Session(options: sessionOptions)
        
        let currentSessionInfo = session.sessionInfo

        try await confirmation(
            "session finished and started new one",
            expectedCount: 2
        ) { sessionUpdated in
            session.start(
                onWillEndSession: { sessionId in
                    #expect(currentSessionInfo.id == sessionId)
                    sessionUpdated()
                    print("session updated 1")
                },
                onDidStartSession: { sessionId in
                    #expect(currentSessionInfo.id != sessionId)
                    sessionUpdated()
                    print("session updated 2")
                }
            )
            await MainActor.run {
                NotificationCenter.default.post(name: UIApplication.didEnterBackgroundNotification, object: nil)
            }

            try await wait(for: 2)
            
            await MainActor.run {
                NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
            }
            
            try await wait(for: 2)
        }
    }
    
    @Test func givenAppLifeCycleBasedSessionWehnDidBecameActiveCalledMultipleTimesThenSessionShouldNotBeRestarted() async throws {
        let sessionOptions = SessionOptions(timeout: 1)
        let session = Session(options: sessionOptions)
        
        let currentSessionInfo = session.sessionInfo

        try await confirmation(
            "session finished and started new one",
            expectedCount: 2
        ) { sessionUpdated in
            session.start(
                onWillEndSession: { sessionId in
                    #expect(currentSessionInfo.id == sessionId)
                    sessionUpdated()
                    print("session updated 1")
                },
                onDidStartSession: { sessionId in
                    #expect(currentSessionInfo.id != sessionId)
                    sessionUpdated()
                    print("session updated 2")
                }
            )
            await MainActor.run {
                NotificationCenter.default.post(name: UIApplication.didEnterBackgroundNotification, object: nil)
            }

            try await wait(for: 2)
            
            await MainActor.run {
                NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
            }
            
            try await wait(for: 0.3)
            
            await MainActor.run {
                NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
            }
            
            try await wait(for: 0.3)
            
            await MainActor.run {
                NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
            }
        }
    }
}
