package com.launchdarkly.observability.context

/**
 * Lightweight context representation for the Observability SDK, independent of
 * `com.launchdarkly.sdk.LDContext`.
 *
 * A context carries a kind/key pair that identifies a user (or entity) in telemetry
 * and session-replay payloads. Multi-kind contexts aggregate several single-kind
 * contexts under one umbrella.
 *
 * ```kotlin
 * // single context
 * val ctx = LDObserveContext.builder("user", "user-123")
 *     .anonymous(true)
 *     .build()
 *
 * // multi context
 * val multi = LDObserveContext.createMulti(
 *     LDObserveContext.create("user", "user-123"),
 *     LDObserveContext.create("device", "iphone"),
 * )
 * ```
 */
class LDObserveContext private constructor(
    val kind: String,
    val key: String,
    val name: String? = null,
    val anonymous: Boolean = false,
    private val contexts: List<LDObserveContext>? = null,
) {
    val isMultiple: Boolean get() = contexts != null

    val individualContextCount: Int get() = contexts?.size ?: 0

    fun getIndividualContext(index: Int): LDObserveContext =
        contexts?.get(index) ?: throw IndexOutOfBoundsException("Not a multi-kind context")

    /**
     * Returns the fully qualified key, matching `LDContext` semantics:
     * - Single "user" context: just [key]
     * - Single non-"user" context: `kind:key`
     * - Multi context: sub-context keys sorted by kind, joined as `kind:key`
     */
    val fullyQualifiedKey: String
        get() = when {
            isMultiple -> contexts!!.sortedBy { it.kind }
                .joinToString(":") { it.fullyQualifiedKey }
            kind == DEFAULT_KIND -> key
            else -> "$kind:$key"
        }

    override fun toString(): String =
        if (isMultiple) "LDObserveContext.multi(${contexts!!.joinToString { it.toString() }})"
        else "LDObserveContext(kind=$kind, key=$key)"

    companion object {
        const val DEFAULT_KIND = "user"

        fun create(kind: String, key: String): LDObserveContext =
            LDObserveContext(kind = kind, key = key)

        fun createMulti(vararg contexts: LDObserveContext): LDObserveContext {
            require(contexts.size >= 2) { "Multi-kind context requires at least 2 contexts" }
            return LDObserveContext(
                kind = "multi",
                key = contexts.joinToString(":") { it.key },
                contexts = contexts.toList()
            )
        }

        fun builder(kind: String, key: String): Builder = Builder(kind, key)
    }

    class Builder(private val kind: String, private val key: String) {
        private var name: String? = null
        private var anonymous: Boolean = false

        fun name(name: String) = apply { this.name = name }
        fun anonymous(anonymous: Boolean) = apply { this.anonymous = anonymous }

        fun build(): LDObserveContext = LDObserveContext(
            kind = kind,
            key = key,
            name = name,
            anonymous = anonymous
        )
    }
}
