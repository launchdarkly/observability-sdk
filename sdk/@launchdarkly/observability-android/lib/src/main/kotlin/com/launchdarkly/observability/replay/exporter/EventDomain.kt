package com.launchdarkly.observability.replay.exporter

enum class EventDomain(val wireValue: String) {
    MEDIA("media"),
    INTERACTION("interaction"),
    IDENTIFY("identify");

    companion object {
        fun fromString(value: String?): EventDomain? {
            return values().firstOrNull { it.wireValue == value }
        }
    }
}


