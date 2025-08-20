import Foundation

public class AtomicDictionary<Key, Value>: CustomDebugStringConvertible, @unchecked Sendable
where Key: Hashable, Key: Sendable {
  private var storage = [Key: Value]()

    private let queue = DispatchQueue(
        label: "com.obsevabability.\(UUID().uuidString)",
        qos: .utility,
        attributes: .concurrent,
        autoreleaseFrequency: .inherit,
        target: .global()
    )

  public init() {}

  public subscript(key: Key) -> Value? {
    get { queue.sync { storage[key] }}
    set { queue.async(flags: .barrier) { [weak self] in self?.storage[key] = newValue } }
  }

  public var debugDescription: String {
    return storage.debugDescription
  }
}
