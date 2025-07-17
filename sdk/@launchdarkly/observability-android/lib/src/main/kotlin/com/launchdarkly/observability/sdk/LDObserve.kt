import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe

class LDObserve(private val client: ObservabilityClient) : Observe {
    override fun recordMetric(metric: Metric) {
        client.recordMetric(metric)
    }

    override fun recordCount(metric: Metric) {
        client.recordCount(metric)
    }

    override fun recordIncr(metric: Metric) {
        client.recordIncr(metric)
    }

    override fun recordHistogram(metric: Metric) {
        client.recordHistogram(metric)
    }

    override fun recordUpDownCounter(metric: Metric) {
        client.recordUpDownCounter(metric)
    }

    companion object {
        // initially a no-op implementation
        val LDObserve: Observe = object : Observe {
            override fun recordMetric(metric: Metric) {}
            override fun recordCount(metric: Metric) {}
            override fun recordIncr(metric: Metric) {}
            override fun recordHistogram(metric: Metric) {}
            override fun recordUpDownCounter(metric: Metric) {}
        }

        fun init(client: ObservabilityClient) {
            LDObserve = LDObserve(client)
        }
    }
}
