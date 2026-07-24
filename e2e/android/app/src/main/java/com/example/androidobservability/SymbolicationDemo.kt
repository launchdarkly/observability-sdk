package com.example.androidobservability

/**
 * A small multi-class call chain used to demonstrate Android Symbols Id Lane
 * symbolication. In a release (R8) build these classes and methods are
 * obfuscated (e.g. `a.b.c`), so the recorded error's stack trace is unreadable
 * until the backend retraces it against the uploaded `mapping.txt` (keyed by the
 * symbols id the SDK reports). The chain spans two classes so the retraced trace
 * shows real class/method/line names across a call boundary.
 */
object CheckoutDemo {
    fun startCheckout(orderId: String): Int = CartPricing.priceOrder(orderId)
}

private object CartPricing {
    fun priceOrder(orderId: String): Int = computeTotal(orderId)

    private fun computeTotal(orderId: String): Int =
        throw Error("Symbols Id Lane demo: checkout failed while pricing order $orderId")
}
