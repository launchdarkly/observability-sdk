import Foundation

func wait(for duration: TimeInterval = 1) async throws {
    try await Task.sleep(for: .seconds(duration))
}
