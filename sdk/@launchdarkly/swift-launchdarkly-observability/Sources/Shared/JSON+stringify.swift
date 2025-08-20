import Foundation

public struct JSON {
    public static func stringify<T: Encodable>(_ value: T) -> String? {
        guard let jsonData = try? JSONEncoder().encode(value) else { return nil }
        let result = String(data: jsonData, encoding: .utf8)
        return result
    }
}
