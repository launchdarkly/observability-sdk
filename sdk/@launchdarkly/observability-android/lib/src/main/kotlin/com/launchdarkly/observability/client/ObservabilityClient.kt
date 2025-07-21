import com.launchdarkly.observability.client.InstrumentationManager
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe
import io.opentelemetry.sdk.resources.Resource

class ObservabilityClient: Observe {
    private val instrumentationManager: InstrumentationManager

    constructor(
        sdkKey: String,
        resource: Resource
    ) {
        this.instrumentationManager = InstrumentationManager(sdkKey, resource)
    }

    override fun recordMetric(metric: Metric) {
        instrumentationManager.recordMetric(metric)
    }

    override fun recordCount(metric: Metric) {
        instrumentationManager.recordCount(metric)
    }

    override fun recordIncr(metric: Metric) {
        instrumentationManager.recordIncr(metric)
    }

    override fun recordHistogram(metric: Metric) {
        instrumentationManager.recordHistogram(metric)
    }

    override fun recordUpDownCounter(metric: Metric) {
        instrumentationManager.recordUpDownCounter(metric)
    }
}