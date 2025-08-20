import Foundation
import OpenTelemetryApi

/// - Parameters:
///     - name: metric name
///     - value: metric's value
///     - attributes: semantic attributes
///     - timestamp: date and time for the metric
public struct Metric {
    public let name: String
    public let value: Double
    public let attributes: [String: AttributeValue]
    public let timestamp: Date? // Could be any Date object representing the date and time for the metric
    
    public init(name: String, value: Double, attributes: [String: AttributeValue] = [:], timestamp: Date? = nil) {
        self.name = name
        self.value = value
        self.attributes = attributes
        self.timestamp = timestamp
    }
}
