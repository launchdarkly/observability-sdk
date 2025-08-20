import OpenTelemetryApi

public protocol Observe {
    /**
     * Record a metric value.
     * @param metric The metric to record
     */
    func recordMetric(metric: Metric)

    /**
     * Record a count metric.
     * @param metric The count metric to record
     */
    func recordCount(metric: Metric)

    /**
     * Record an increment metric.
     * @param metric The increment metric to record
     */
    func recordIncr(metric: Metric)

    /**
     * Record a histogram metric.
     * @param metric The histogram metric to record
     */
    func recordHistogram(metric: Metric)

    /**
     * Record an up/down counter metric.
     * @param metric The up/down counter metric to record
     */
    func recordUpDownCounter(metric: Metric)

    /**
     * Record an error.
     * @param error The error to record
     * @param attributes The attributes to record with the error
     * @param options The options to record with the error
     */
    func recordError(error: Error, attributes: [String: AttributeValue])

    /**
     * Record a log message.
     * @param message The log message to record
     * @param severity The severity of the log message
     * @param attributes The attributes to record with the log message
     */
    func recordLog(message: String, severity: Severity, attributes: [String: AttributeValue])

    /**
     * Start a span.
     * @param name The name of the span
     * @param attributes The attributes to record with the span
     */
    func startSpan(name: String, attributes: [String: AttributeValue]) -> Span
    
    func flush()
}
