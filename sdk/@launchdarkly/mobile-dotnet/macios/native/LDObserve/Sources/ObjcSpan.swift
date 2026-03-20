import Foundation
import OpenTelemetryApi

/// Objective-C–visible representation of a `System.Diagnostics.Activity` that
/// conforms to the OpenTelemetry `Span` protocol (SpanBase).
///
/// The @objc init accepts only primitive / Foundation types so the C# binding
/// can construct instances from `Activity` properties.  Swift consumers can
/// use it as a regular `Span`.
@objc(ObjcSpan)
public final class ObjcSpan: NSObject {

    // MARK: - Stored state

    private let _context: SpanContext
    private var _name: String
    private let _kind: SpanKind
    private var _status: Status = .unset
    private var _isRecording: Bool = true
    private var _attributes: [String: AttributeValue] = [:]
    private var _events: [(name: String, timestamp: Date, attributes: [String: AttributeValue])] = []

    // MARK: - @objc init (for C# bridge)

    /// - Parameters:
    ///   - traceId:   32-char hex trace identifier.
    ///   - spanId:    16-char hex span identifier.
    ///   - name:      Display name / operation name.
    ///   - statusCode: 0 = unset, 1 = ok, 2 = error.
    ///   - attributes: String-keyed dictionary of primitive values.
    @objc public init(traceId: String,
                      spanId: String,
                      name: String,
                      statusCode: Int,
                      attributes: NSDictionary?) {
        self._context = SpanContext.create(
            traceId: TraceId(fromHexString: traceId),
            spanId:  SpanId(fromHexString: spanId),
            traceFlags: TraceFlags(fromByte: TraceFlags.sampled),
            traceState: TraceState()
        )
        self._name = name
        self._kind = .internal

        switch statusCode {
        case 1:  _status = .ok
        case 2:  _status = .error(description: "")
        default: _status = .unset
        }

        super.init()

        if let dict = attributes as? [String: Any] {
            for (key, value) in dict {
                switch value {
                case let s as String:  _attributes[key] = .string(s)
                case let b as Bool:    _attributes[key] = .bool(b)
                case let i as Int:     _attributes[key] = .int(i)
                case let d as Double:  _attributes[key] = .double(d)
                default:               _attributes[key] = .string(String(describing: value))
                }
            }
        }
    }
}

// MARK: - OpenTelemetry SpanBase conformance

extension ObjcSpan: Span {

    public var isRecording: Bool { _isRecording }
    public var context: SpanContext { _context }

    public var status: Status {
        get { _status }
        set { _status = newValue }
    }

    public var name: String {
        get { _name }
        set { _name = newValue }
    }

    public var kind: SpanKind { _kind }

    public func setAttribute(key: String, value: AttributeValue?) {
        if let value = value {
            _attributes[key] = value
        } else {
            _attributes.removeValue(forKey: key)
        }
    }

    public func addEvent(name: String) {
        _events.append((name: name, timestamp: Date(), attributes: [:]))
    }

    public func addEvent(name: String, attributes: [String: AttributeValue]) {
        _events.append((name: name, timestamp: Date(), attributes: attributes))
    }

    public func addEvent(name: String, timestamp: Date) {
        _events.append((name: name, timestamp: timestamp, attributes: [:]))
    }

    public func addEvent(name: String, attributes: [String: AttributeValue], timestamp: Date) {
        _events.append((name: name, timestamp: timestamp, attributes: attributes))
    }

    public func end() { _isRecording = false }
    public func end(time: Date) { _isRecording = false }
}
