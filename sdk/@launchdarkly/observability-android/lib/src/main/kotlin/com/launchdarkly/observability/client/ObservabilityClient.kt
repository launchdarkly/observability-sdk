import com.launchdarkly.observability.client.InstrumentationManager
import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe
import com.launchdarkly.sdk.android.LDClient
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.resources.Resource

public class ObservabilityClient: Observe {
    private val instrumentationManager: InstrumentationManager

    constructor(
        sdkKey: String,
        client: LDClient,
        resource: Resource
    ) {
        this.instrumentationManager = InstrumentationManager(sdkKey, client, resource)
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

    override fun recordError(error: Error, attributes: Attributes) {
        instrumentationManager.recordError(error, attributes)
    }

    override fun recordLog(message: String, level: String, attributes: Attributes) {
        instrumentationManager.recordLog(message, level, attributes)
    }
}
