import Foundation

/// Serialises native dictionaries so they can cross the Skip bridge as strings.
///
/// `[String: Any]` is not a bridgeable parameter type, and JSON keeps the same shape on
/// both sides of the boundary.
enum JSONBridge {
    static func string(from dictionary: [String: Any]) -> String {
        guard !dictionary.isEmpty,
              let data = try? JSONSerialization.data(withJSONObject: dictionary),
              let text = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return text
    }

    static func dictionary(from json: String) -> [String: Any]? {
        guard let data = json.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data),
              let dictionary = object as? [String: Any] else {
            return nil
        }
        return dictionary
    }
}
