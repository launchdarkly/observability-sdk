package com.launchdarkly.observability.utils

inline fun <T1, R> notNull(a1: T1?, block: (T1) -> R?): R? {
    return if (a1 != null) block(a1) else null
}

inline fun <T1, T2, R> notNull(a1: T1?, a2: T2?, block: (T1, T2) -> R?): R? {
    return if (a1 != null && a2 != null) block(a1, a2) else null
}

inline fun <T1, T2, T3, R> notNull(a1: T1?, a2: T2?, a3: T3?, block: (T1, T2, T3) -> R?): R? {
    return if (a1 != null && a2 != null && a3 != null) block(a1, a2, a3) else null
}

inline fun <T1, T2, T3, T4, R> notNull(a1: T1?, a2: T2?, a3: T3?, a4: T4?, block: (T1, T2, T3, T4) -> R?): R? {
    return if (a1 != null && a2 != null && a3 != null && a4 != null) block(a1, a2, a3, a4) else null
}
