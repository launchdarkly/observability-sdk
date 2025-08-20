public struct SessionOptions: Sendable {
    let timeout: TimeInterval
    let isDebug: Bool

    public init(timeout: TimeInterval, isDebug: Bool = true) {
        self.timeout = timeout
        self.isDebug = isDebug
    }
}
