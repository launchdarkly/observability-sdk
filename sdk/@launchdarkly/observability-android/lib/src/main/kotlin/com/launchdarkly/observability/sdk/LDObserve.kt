import com.launchdarkly.observability.interfaces.Metric
import com.launchdarkly.observability.interfaces.Observe

class LDObserve : Observe {
    override fun recordMetric(metric: Metric) {
        TODO("Not yet implemented")
    }

    override fun recordCount(metric: Metric) {
        TODO("Not yet implemented")
    }

    override fun recordIncr(metric: Metric) {
        TODO("Not yet implemented")
    }

    override fun recordHistogram(metric: Metric) {
        TODO("Not yet implemented")
    }

    override fun recordUpDownCounter(metric: Metric) {
        TODO("Not yet implemented")
    }

    companion object {
        val LDObserve: LDObserve = LDObserve()
    }
}
